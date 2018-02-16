package com.paddypowerbetfair.rabbitmq

import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy
import akka.actor.Props
import akka.actor.ActorLogging
import java.util.UUID
import com.rabbitmq.client.ShutdownSignalException
import com.rabbitmq.client.ShutdownListener
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Address
import scala.util.Try
import akka.actor.PoisonPill
import akka.actor.Actor
import java.util.concurrent.ExecutorService
import scala.util.Random
import akka.actor.SupervisorStrategy._

class ConnectionActor(connectionFactory: ConnectionFactory, executor: ExecutorService, addresses: List[Address]) extends Actor with ActorLogging {

  val connection = connectionFactory.newConnection(executor, Random.shuffle(addresses).toArray)
  connection.addShutdownListener(shutdownListener)

  def receive: Receive = {
    case consumersRegistration @ RegisterConsumer(queueName, _, _, _, _, _, _, _) =>
      context.actorOf(ConsumerForwarderActor.props(connection, consumersRegistration), queueName + UUID.randomUUID().toString())
      log.debug("A consumer forwarder actor was created")
    case publisherRegistration @ RegisterPublisher(_, _, publisherConfirms, _) =>
      val publishActorClass = if (publisherConfirms) classOf[ConfirmPublishActor] else classOf[BasicPublishActor]
      context.actorOf(Props(publishActorClass, connection, publisherRegistration), "publisher" + UUID.randomUUID().toString())
      log.debug("A publisher actor was created")
    case cause: ShutdownSignalException =>
      log.debug("connection actor shutdown signal")
      if (cause.isInitiatedByApplication()) {
        log.warning("Shutting down by application request")
        self ! PoisonPill
      } else {
        log.warning(s"RabbitMq connection shutdown due to ${cause.getMessage} ${cause.getStackTrace.mkString("\n")}")
        throw cause
      }
  }

  private def shutdownListener = new ShutdownListener() {
    def shutdownCompleted(cause: ShutdownSignalException) {
      log.debug("RabbitMq connection shutdown")
      self ! cause
    }
  }

  override def postStop(): Unit = {
    log.debug("Stopping connection actor")
    Try(connection.close())
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case ConsumerRegistrationException => Restart
    case _: Any =>
      log.debug("channel crashed with hard error, escalating")
      Escalate
  }
}




