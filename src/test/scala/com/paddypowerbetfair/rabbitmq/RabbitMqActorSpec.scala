package com.paddypowerbetfair.rabbitmq

import java.io.IOException
import java.util.HashMap
import java.util.concurrent.ExecutorService

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestActor, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import com.rabbitmq.client._
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => mockitoEq, _}
import org.mockito.Mockito._
import org.scalatest._
import org.scalatest.mockito.MockitoSugar

import scala.collection.JavaConverters._
import scala.concurrent.duration._

abstract class RabbitMqActorSpec(ackActorPerMessage: Boolean) extends TestKit(ActorSystem("testsystem"))
    with ImplicitSender
    with FlatSpecLike
    with Matchers
    with BeforeAndAfterAll
    with DiagrammedAssertions {

  implicit val timeout: Timeout = 5000.seconds

  trait RabbitMqFixture extends MockitoSugar {

    val testAckStrategy = new AckStrategy {
      override def ack(channel: Channel, deliveryTag: Long): Unit = channel.basicAck(deliveryTag, false)
      override def nack(channel: Channel, deliveryTag: Long): Unit = channel.basicNack(deliveryTag, false, true)
      override def expire(channel: Channel, deliveryTag: Long): Unit = channel.basicAck(deliveryTag, true)
    }

    def sleep() = Thread.sleep(100)

    val cf = mock[ConnectionFactory]
    val conn = mock[Connection]
    val ch = mock[Channel]

    val props = Props(classOf[RabbitMqActor], cf, List(new Address("host1")), 10.millis)
    val delivery = Delivery("tag", new Envelope(1L, false, "exchange", "routingKey"), new AMQP.BasicProperties.Builder().build(), "test".getBytes)

    val consumer = TestProbe()

    def autoPilot(ack: Any) = new TestActor.AutoPilot {
      def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
        sender ! ack;
        TestActor.KeepRunning
      }
    }
  }

  trait WithConsumerFixture extends RabbitMqFixture {
    when(cf.newConnection(any[ExecutorService](), any[Array[Address]])).thenReturn(conn)
    when(conn.createChannel()).thenReturn(ch)
    when(ch.isOpen).thenReturn(true)
    val rabbitMq: TestActorRef[RabbitMqActor] = TestActorRef(props)
    val registrationTimeout = 50.milliseconds

    rabbitMq ! RegisterConsumer("test", consumer.ref, List(
      ExchangeDeclare("test1", "fanout", true, false),
      ExchangeBind("test2", "test1", ""),
      QueueDeclare("test", true, false, false),
      QueueBind("test", "test2", ""),
      BasicQos(500)), registrationTimeout,
      withAckActors = ackActorPerMessage,
      ackStrategy = testAckStrategy)
    val captor = ArgumentCaptor.forClass[Consumer, Consumer](classOf[Consumer])
    sleep

    def verifyAndGetConsumer(): Consumer = {
      verify(ch).basicConsume(anyString(), anyBoolean(), anyString(), anyBoolean(), anyBoolean(), any(classOf[java.util.Map[String, AnyRef]]), captor.capture())
      captor.getValue
    }
  }

  "The rabbitmq actor" should "register a consumer and send it a message" in new WithConsumerFixture {
    expectMsg(RegisterOk)
    verify(ch).exchangeBind("test2", "test1", "")
    verify(ch).exchangeDeclare("test1", "fanout", true, false, new HashMap())
    verify(ch).queueDeclare("test", true, false, false, new HashMap())
    verify(ch).queueBind("test", "test2", "", new HashMap())
    verify(ch).basicQos(500)

    verify(ch).basicConsume(mockitoEq("test"), mockitoEq(false), mockitoEq(""), mockitoEq(false), mockitoEq(false), mockitoEq(Map.empty[String, AnyRef].asJava), captor.capture())
    captor.getValue.handleDelivery(delivery.tag, delivery.envelope, delivery.properties, delivery.body)
    consumer.expectMsg(delivery)
  }

  it should "NOT notify uninterested registered actors if the connection drops" in new MockitoSugar {
    val cf = mock[ConnectionFactory]
    when(cf.newConnection(any[ExecutorService](), any[Array[Address]])).thenReturn(mock[Connection])

    val rmq = TestActorRef(new RabbitMqActor(cf, Nil, 10.millis))
    val reg = RegisterConsumer("test", self, List(), 50.millis,
      withAckActors = ackActorPerMessage,
      ackStrategy = new DefaultAckStrategy)
    rmq.underlyingActor.registrations :+= reg

    rmq.underlyingActor.supervisorStrategy.decider.apply(new IOException("asd"))

    expectNoMessage(remainingOrDefault)
  }

  it should "notify interested registered actors if the connection drops" in new MockitoSugar {
    val cf = mock[ConnectionFactory]
    when(cf.newConnection(any[ExecutorService](), any[Array[Address]])).thenReturn(mock[Connection])

    val rmq = TestActorRef(new RabbitMqActor(cf, Nil, 10.millis))
    val reg = RegisterConsumer("test", self, List(), 50.millis,
      withAckActors = ackActorPerMessage,
      ackStrategy = new DefaultAckStrategy,
      connectionDeathWatch = true)
    rmq.underlyingActor.registrations :+= reg

    rmq.underlyingActor.supervisorStrategy.decider.apply(new IOException("asd"))

    expectMsg(RabbitMqConnectionDropped)
  }

  it should "ack messages after the registration timeout" in new WithConsumerFixture {
    verifyAndGetConsumer().handleDelivery(delivery.tag, delivery.envelope, delivery.properties, delivery.body)
    consumer.expectMsg(delivery)
    Thread.sleep(registrationTimeout.toMillis + 50)
    verify(ch).basicAck(1L, true)
  }

  it should "not ack or nack messages when the consumer says so after the registration timeout" in new WithConsumerFixture {
    verifyAndGetConsumer().handleDelivery(delivery.tag, delivery.envelope, delivery.properties, delivery.body)
    consumer.expectMsg(delivery)
    Thread.sleep(registrationTimeout.toMillis + 50)
    consumer.sender ! (if(ackActorPerMessage)Ack else Ack(1L))
    verify(ch, times(1)).basicAck(mockitoEq(1L), anyBoolean())
  }

  it should "ack messages when the consumer says so" in new WithConsumerFixture {
    consumer.setAutoPilot(autoPilot(if(ackActorPerMessage)Ack else Ack(1L)))
    verifyAndGetConsumer().handleDelivery(delivery.tag, delivery.envelope, delivery.properties, delivery.body)
    consumer.expectMsg(delivery)
    sleep
    verify(ch).basicAck(1L, false)
  }

  it should "nack messages when the consumer says so" in new WithConsumerFixture {
    consumer.setAutoPilot(autoPilot(if(ackActorPerMessage)Nack else Nack(1L)))
    verifyAndGetConsumer().handleDelivery(delivery.tag, delivery.envelope, delivery.properties, delivery.body)
    consumer.expectMsg(delivery)
    sleep
    verify(ch).basicNack(1L, false, true)
  }

  it should "try reconnect after one second if connection creation fails" in new RabbitMqFixture {
    when(cf.newConnection(any[ExecutorService](), any[Array[Address]])).thenThrow(new IOException("dont worry about this")).thenReturn(conn)
    when(conn.createChannel()).thenReturn(ch)
    when(ch.isOpen).thenReturn(true)
    val rabbitMq = TestActorRef(props)
    rabbitMq ! RegisterConsumer("test", consumer.ref, List(), withAckActors = ackActorPerMessage, ackStrategy = testAckStrategy)
    sleep
    verify(ch).basicConsume(mockitoEq("test"), mockitoEq(false), mockitoEq(""), mockitoEq(false), mockitoEq(false),
      mockitoEq(Map.empty[String, AnyRef].asJava), any[Consumer])
  }

  it should "deregisters dead consumers" in new WithConsumerFixture {
    consumer.ref ! PoisonPill
    sleep
    verify(ch).close()
  }

  it should "reconnect on connection shutdown" in new RabbitMqFixture {
    when(cf.newConnection(any[ExecutorService](), any[Array[Address]])).thenReturn(conn)
    when(conn.createChannel()).thenReturn(ch)
    val rabbitMq = TestActorRef(props)
    sleep
    val captor = ArgumentCaptor.forClass[ShutdownListener, ShutdownListener](classOf[ShutdownListener])
    verify(conn).addShutdownListener(captor.capture())
    captor.getValue.shutdownCompleted(new ShutdownSignalException(false, false, null, null))
    sleep
    verify(cf, times(2)).newConnection(any[ExecutorService](), any[Array[Address]])
  }

  it should "shutdown on connection shutdown initiated by application" in new RabbitMqFixture {
    when(cf.newConnection(any[ExecutorService](), any[Array[Address]])).thenReturn(conn)
    when(conn.createChannel()).thenReturn(ch)
    val rabbitMq = TestActorRef(props)
    sleep
    val captor = ArgumentCaptor.forClass[ShutdownListener, ShutdownListener](classOf[ShutdownListener])
    verify(conn).addShutdownListener(captor.capture())
    captor.getValue.shutdownCompleted(new ShutdownSignalException(false, true, null, null))
    sleep
    verify(cf, times(1)).newConnection(any[ExecutorService](), any[Array[Address]])
    verify(conn).close()
  }

  it should "reconnect on channel shutdown" in new WithConsumerFixture {
    verifyAndGetConsumer().handleShutdownSignal("", new ShutdownSignalException(false, false, null, null))
    sleep
    verify(cf, times(1)).newConnection(any[ExecutorService](), any[Array[Address]])
  }

  it should "reconnect on queue deleted" in new WithConsumerFixture {
    verifyAndGetConsumer().handleCancel("test:queue was deleted")
    sleep
    verify(cf, times(2)).newConnection(any[ExecutorService](), any[Array[Address]])
  }

  it should "shutdown on consumer cancel" in new WithConsumerFixture {
    verifyAndGetConsumer().handleCancelOk("test:cancel")
    sleep
    verify(ch).close()
  }
}

class AckActorsRabbitMqSpec extends RabbitMqActorSpec(true)

class SingleChannelActorSpec extends RabbitMqActorSpec(false)