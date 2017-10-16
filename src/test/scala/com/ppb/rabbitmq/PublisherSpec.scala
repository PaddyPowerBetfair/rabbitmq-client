package com.ppb.rabbitmq

import java.io.IOException
import java.util.HashMap
import java.util.concurrent.ExecutorService

import scala.concurrent.duration.DurationInt
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.verify
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.scalatest.{BeforeAndAfterAll, DiagrammedAssertions, FlatSpecLike, Matchers}
import org.scalatest.mock.MockitoSugar
import com.rabbitmq.client.Address
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConfirmListener
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.MessageProperties
import com.rabbitmq.client.ShutdownListener
import com.rabbitmq.client.ShutdownSignalException
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.testkit.ImplicitSender
import akka.testkit.TestActor
import akka.testkit.TestActorRef
import akka.testkit.TestKit
import akka.testkit.TestProbe
import org.scalatest.concurrent.Eventually

class PublisherSpec extends TestKit(ActorSystem("testsystem", RabbitMqActorSpec.config))
    with ImplicitSender
    with FlatSpecLike
    with Matchers
    with BeforeAndAfterAll
    with DiagrammedAssertions {

  class WithPublisherFixture(confirm: Boolean = false) extends MockitoSugar {

    val cf = mock[ConnectionFactory]
    val conn = mock[Connection]
    val ch = mock[Channel]

    val props = Props(classOf[RabbitMqActor], cf, List(new Address("host1")), 2 seconds)

    def autoPilot(ack: Any) = new TestActor.AutoPilot {
      def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
        sender ! ack;
        TestActor.KeepRunning
      }
    }
    val publisherProbe = TestProbe()

    val data = "this is a test".getBytes
    val msg = BasicPublish("testExchange", "testRoutingKey", MessageProperties.BASIC, data)
    when(cf.newConnection(any[ExecutorService](), any[Array[Address]])).thenReturn(conn)
    when(conn.createChannel()).thenReturn(ch)
    val rabbitMq = system.actorOf(props)
    rabbitMq ! RegisterPublisher(publisherProbe.ref, List(ExchangeDeclare("test1", "fanout", true, false)), confirm)
    expectMsg(RegisterOk)
    val PublisherChannelActor(channelActorRef) = publisherProbe.expectMsgClass(classOf[PublisherChannelActor])
  }

  "The rabbitmq actor" should "register a publisher, make declarations and send it a channel actor" in new WithPublisherFixture {
    verify(ch).exchangeDeclare("test1", "fanout", true, false, new HashMap())
  }

  it should "send a new channel actor on reconnection" in new WithPublisherFixture {
    val shutdownListenerCaptor = ArgumentCaptor.forClass[ShutdownListener, ShutdownListener](classOf[ShutdownListener])
    Eventually.eventually {
      verify(conn).addShutdownListener(shutdownListenerCaptor.capture())
    }
    shutdownListenerCaptor.getValue.shutdownCompleted(new ShutdownSignalException(false, false, null, null))
    publisherProbe.expectMsgClass(classOf[PublisherChannelActor])
  }

  "The basic channel actor" should "basicPublish the message on the channel" in new WithPublisherFixture {
    channelActorRef.tell(msg, publisherProbe.ref)
    publisherProbe.expectMsg(Ack)
    verify(ch).basicPublish("testExchange", "testRoutingKey", false, false, MessageProperties.BASIC, data)
  }

  it should "Nack messages on channel crash" in new WithPublisherFixture {
    val probe = TestProbe()
    when(ch.basicPublish("testExchange", "testRoutingKey", false, false, MessageProperties.BASIC, data)).thenThrow(new IOException("don't worry"))
    channelActorRef.tell(msg, probe.ref)
    probe.expectMsg(Nack)
    publisherProbe.expectMsgClass(classOf[PublisherChannelActor])
  }

  "The confirm publisher actor" should "ack messages with a confirm listener" ignore new WithPublisherFixture(true) {
    Eventually.eventually {
      verify(ch).exchangeDeclare("test1", "fanout", true, false, new HashMap())
      verify(ch).confirmSelect()
    }
    val confirmListenerCaptor: ArgumentCaptor[ConfirmListener] = ArgumentCaptor.forClass[ConfirmListener, ConfirmListener](classOf[ConfirmListener])
    verify(ch).addConfirmListener(confirmListenerCaptor.capture())
    when(ch.getNextPublishSeqNo()).thenReturn(1l, 2l, 3l)

    val publishers = (1 to 3) map { _ => TestProbe() }
    for (pub <- publishers) channelActorRef.tell(msg, pub.ref)
    confirmListenerCaptor.getValue().handleAck(2, true)
    publishers(0).expectMsg(Ack)
    publishers(1).expectMsg(Ack)
    confirmListenerCaptor.getValue().handleAck(3, false)
    publishers(2).expectMsg(Ack)
  }

  it should "nack messages with a confirm listener" ignore new WithPublisherFixture(true) {
    Eventually.eventually {
      verify(ch).confirmSelect()
    }
    val confirmListenerCaptor = ArgumentCaptor.forClass[ConfirmListener, ConfirmListener](classOf[ConfirmListener])
    verify(ch).addConfirmListener(confirmListenerCaptor.capture())
    when(ch.getNextPublishSeqNo()).thenReturn(1l, 2l, 3l)

    val publishers = (1 to 3) map { _ => TestProbe() }
    for (pub <- publishers) channelActorRef.tell(msg, pub.ref)
    confirmListenerCaptor.getValue().handleNack(2, true)
    publishers(0).expectMsg(Nack)
    publishers(1).expectMsg(Nack)
    confirmListenerCaptor.getValue().handleNack(3, false)
    publishers(2).expectMsg(Nack)
  }

  it should "nack all messages if it dies" in new WithPublisherFixture(true) {
    val probe = TestProbe()
    when(ch.basicPublish("testExchange", "testRoutingKey", false, false, MessageProperties.BASIC, data)).thenThrow(new IOException("don't worry"))
    channelActorRef.tell(msg, probe.ref)
    probe.expectMsg(Nack)
    publisherProbe.expectMsgClass(classOf[PublisherChannelActor])
  }

  "The Stashing publisher" should "stash message before the channel arrives" in {
    val stashingPub = TestActorRef(Props(classOf[StashingPublisher]))
    val msg = BasicPublish("1", "2", MessageProperties.BASIC, "".getBytes)
    stashingPub ! msg
    val channel = TestProbe()
    stashingPub ! PublisherChannelActor(channel.ref)
    channel.expectMsg(msg)

    channel.ref ! PoisonPill
    stashingPub ! msg
    val channel2 = TestProbe()
    stashingPub ! PublisherChannelActor(channel2.ref)
    channel2.expectMsg(msg)
  }

  it should "switch channels on PublisherChannelActor message received" in {
    val msg = BasicPublish("1", "2", MessageProperties.BASIC, "".getBytes)
    val stashingPub = TestActorRef(Props(classOf[StashingPublisher]))
    val channel = TestProbe()
    val channel2 = TestProbe()
    stashingPub ! PublisherChannelActor(channel.ref)
    stashingPub ! PublisherChannelActor(channel2.ref)
    stashingPub ! msg
    channel2.expectMsg(msg)
    channel.ref ! PoisonPill
    stashingPub ! msg
    channel2.expectMsg(msg)
  }

}