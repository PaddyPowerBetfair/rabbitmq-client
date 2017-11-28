package com.paddypowerbetfair.rabbitmq

import akka.actor.Actor._
import akka.actor.SupervisorStrategy._
import akka.actor._
import com.rabbitmq.client._
import scala.concurrent.duration._
import scala.util.{Failure, Try}

//Consumer outgoing messages
case class Delivery(tag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte])

//Consumer accepted responses
trait AckOrNack
case object Ack extends AckOrNack
case class Ack(deliveryTag: Long) extends AckOrNack
case object Nack extends AckOrNack
case class Nack(deliveryTag: Long) extends AckOrNack

private case object Expire
private case class Expire(deliveryTag: Long)

private case class RetryRegistration(attempt: Int)

case object ConsumerRegistrationException extends RuntimeException

trait DeliveryHandler {
  def handleRabbitMessages: Receive
}

trait AckActorDeliveryHandler extends DeliveryHandler {
  self: ConsumerForwarderActor =>
  def handleRabbitMessages: Receive = {
    case delivery: Delivery =>
      val acker = context.actorOf(Props(classOf[AckActor], channel, delivery.envelope.getDeliveryTag, registration.timeout, registration.ackStrategy))
      registration.consumer.tell(delivery, acker)
  }
}

trait ChannelActorDeliveryHandler extends DeliveryHandler {
  this: ConsumerForwarderActor =>

  var waitingForAckOrNack = Map.empty[Long, Cancellable]
  def handleRabbitMessages: Receive = {
    case delivery: Delivery =>
      val expiryTask: Cancellable =
        context.system.scheduler.scheduleOnce(registration.timeout, self, Expire(delivery.envelope.getDeliveryTag))(context.dispatcher)
      waitingForAckOrNack += (delivery.envelope.getDeliveryTag -> expiryTask)
      registration.consumer ! delivery
    case Ack(deliveryTag) if waitingForAckOrNack.contains(deliveryTag) =>
      registration.ackStrategy.ack(channel, deliveryTag)
      waitingForAckOrNack.get(deliveryTag).foreach(_.cancel())
      waitingForAckOrNack -= deliveryTag
    case Nack(deliveryTag) if waitingForAckOrNack.contains(deliveryTag) =>
      registration.ackStrategy.nack(channel, deliveryTag)
      waitingForAckOrNack.get(deliveryTag).foreach(_.cancel())
      waitingForAckOrNack -= deliveryTag
    case Expire(deliveryTag) if waitingForAckOrNack.contains(deliveryTag) =>
      registration.ackStrategy.expire(channel, deliveryTag)
      waitingForAckOrNack -= deliveryTag
  }
}

object ConsumerForwarderActor {

  val MaxFailedRegistrationAttempts = 5

  def props(connection: Connection, registration: RegisterConsumer): Props = {
    if(registration.withAckActors) {
      Props(new ConsumerForwarderActor(connection, registration) with AckActorDeliveryHandler)
    }
    else {
      Props(new ConsumerForwarderActor(connection, registration) with ChannelActorDeliveryHandler)
    }
  }
}

class ConsumerForwarderActor(protected val connection: Connection, protected val registration: RegisterConsumer)
    extends ChannelActor(connection) with DeclarationAdapter {
  this: DeliveryHandler =>

  import scala.collection.JavaConverters._
  import ConsumerForwarderActor._

  override def preStart(): Unit = {
    super.preStart()
    tryToRegister(1)
  }

  def receive: Receive = handleRabbitMessages.orElse({
    case Terminated(actor) =>
      log.warning(s"Consumer for ${registration.queueName} shutdown for ${actor.toString()}")
      self ! PoisonPill
    case RetryRegistration(attemptNo) if attemptNo <= MaxFailedRegistrationAttempts =>
      tryToRegister(attemptNo)
    case RetryRegistration =>
      log.warning(s"Consumer for ${registration.queueName} failed to register after $MaxFailedRegistrationAttempts attempts")
      throw ConsumerRegistrationException
    case e:ShutdownSignalException if e.isHardError == false =>
      throw ConsumerRegistrationException
    case e: Exception =>
      log.warning(s"Consumer for ${registration.queueName} shutdown, with error ${e.getMessage}")
      throw e
  })

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _: Any =>
      log.debug("ack actor crashed, escalating")
      Escalate
  }

  def register(): Unit = {
    context.watch(registration.consumer)

    channel.addShutdownListener(new ShutdownListener() {
      override def shutdownCompleted(sig: ShutdownSignalException): Unit = {
        log.warning(s"Channel ShutdownListener for ${registration.queueName}, channel or the underlying connection has been shut down. Reason: ${sig.getMessage}")
        self ! sig
      }
    })

    registration.declarations foreach { declare }
    channel.basicConsume(registration.queueName, false,
        registration.basicConsumeOptions.consumerTag,
        registration.basicConsumeOptions.noLocal,
        registration.basicConsumeOptions.exclusive,
        registration.basicConsumeOptions.args.asJava,
        new DefaultConsumer(channel) {
          override def handleCancelOk(consumerTag: String): Unit = {
            log.debug("handleCancelOk, client called channel.cancel(consumerTag)")
            self ! PoisonPill
          }

          override def handleCancel(consumerTag: String): Unit = {
            log.debug("handleCancel, queue was deleted")
            self ! new IllegalStateException(consumerTag)
          }

          override def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit = {
            log.warning(s"handleShutdownSignal, Called when either the channel or the underlying connection has been shut down. Reason: ${sig.getMessage}")
            self ! sig
          }

          override def handleDelivery(tag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte]): Unit = {
            self ! Delivery(tag, envelope, properties, body)
          }
        })
  }

  def tryToRegister(attempt: Int): Unit = {
    Try(register()) match {
      case Failure(e) =>
        log.warning(s"Failed to connect to ${registration.queueName}, retry attempt $attempt of $MaxFailedRegistrationAttempts with error ${e.getMessage}")
        context.system.scheduler.scheduleOnce(attempt.second, self, RetryRegistration(attempt + 1))(context.dispatcher)
      case _ => log.warning(s"Consumer registered successfully at ${attempt} attempt")
    }
  }

}