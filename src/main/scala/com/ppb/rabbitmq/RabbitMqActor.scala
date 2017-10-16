package com.ppb.rabbitmq

import java.util.UUID
import java.util.concurrent.{ExecutorService, Executors}

import akka.actor.SupervisorStrategy._
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import com.rabbitmq.client.{Address, ConnectionFactory}

import scala.collection.immutable.Queue
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.control.NonFatal

//RabbiqMqActor accepted messages
trait Registration {
  def registeredActor: ActorRef
  def connectionDeathWatch: Boolean
}

case class RegisterConsumer(queueName: String, consumer: ActorRef, declarations: List[Declaration],
    timeout: FiniteDuration = 5 seconds, ackStrategy: AckStrategy = new DefaultAckStrategy,
    basicConsumeOptions: BasicConsumeOptions = BasicConsumeOptions("", false, false, Map.empty),
    withAckActors: Boolean = true, connectionDeathWatch: Boolean = false) extends Registration {
  def registeredActor: ActorRef = consumer
  def listenToConnectionDrops: Boolean = connectionDeathWatch
}
case class BasicConsumeOptions(consumerTag: String, noLocal: Boolean, exclusive: Boolean, args: Map[String, AnyRef])

case class RegisterPublisher(publisher: ActorRef, declarations: List[Declaration],
    publisherConfirms: Boolean = false, connectionDeathWatch: Boolean = false) extends Registration {
  def registeredActor: ActorRef = publisher
  def listenToConnectionDrops: Boolean = connectionDeathWatch
}

//RabbitMqActor responses
case object RegisterOk
case object RabbitMqConnectionDropped


//Internal RabbitMqActor messages
private[rabbitmq] case object Reconnect

class RabbitMqActor(connectionFactory: ConnectionFactory, addresses: List[Address], retryDelay: FiniteDuration) extends Actor with ActorLogging {
  import context.dispatcher

  def this(connectionFactory: ConnectionFactory, addresses: List[Address]) = this(connectionFactory, addresses, 5 second)

  var registrations = Queue[Registration]()
  var connectionActor = createConnectionActor()
  val executor: ExecutorService = Executors.newFixedThreadPool(8)
  var lastFailure = System.nanoTime()

  def receive: Receive = {
    case registration: Registration =>
      context.watch(registration.registeredActor)
      registrations :+= registration
      connectionActor ! registration
      sender() ! RegisterOk
      log.debug("Consumer/Publisher was registered: {}", registration)
    case Terminated(actor) =>
      registrations = registrations.filter { _.registeredActor != actor }
      log.info("A registered consumer was removed")
    case Reconnect =>
      connectionActor = createConnectionActor()
      registrations foreach { connectionActor ! _ }
  }

  private def createConnectionActor() = {
    context.actorOf(Props(classOf[ConnectionActor], connectionFactory, executor, addresses), "rabbitmq-connection-actor" + UUID.randomUUID().toString())
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case NonFatal(e) =>
      val nextRetryDelay = calculateRetryDelay()
      log.warning("Connection actor failed, will recreate in: {}ms", nextRetryDelay.toMillis)
      context.system.scheduler.scheduleOnce(nextRetryDelay, self, Reconnect)
      registrations filter { _.connectionDeathWatch } foreach { _.registeredActor ! RabbitMqConnectionDropped }
      Stop
    case e: Any =>
      log.error(e, "Connection failed")
      Escalate
  }

  private def calculateRetryDelay(): FiniteDuration = {
    val durationSinceLastFailure = Duration.fromNanos(System.nanoTime() - lastFailure) 
    lastFailure = System.nanoTime()
    log.debug("Time since last failure {}ms", durationSinceLastFailure.toMillis)
    if (durationSinceLastFailure > retryDelay) Duration.Zero
    else retryDelay - durationSinceLastFailure
  }

  override def postStop(): Unit = {
    executor.shutdown()
  }
}