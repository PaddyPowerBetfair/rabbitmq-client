package com.paddypowerbetfair.rabbitmq.basicsample

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.paddypowerbetfair.rabbitmq._
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Address
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}


object BasicSampleApp extends App {

  val logger = LoggerFactory.getLogger(BasicSampleApp.getClass)
  val addresses: List[com.rabbitmq.client.Address] = List(new Address("localhost", 5672))

  implicit val system: ActorSystem = ActorSystem()
  implicit val timeout: Timeout = Timeout(5.seconds)

  val connectionFactory = new com.rabbitmq.client.ConnectionFactory()

  val rabbitActor: ActorRef = system.actorOf(Props(new RabbitMqActor(connectionFactory, addresses)), "rabbitActor")

  val myPublishingActor: ActorRef = system.actorOf(MyPublishingActor.props)
  val myConsumingActor: ActorRef = system.actorOf(MyConsumingActor.props)

  val headers = Map.empty[String, AnyRef]
  val noRoutingKey = ""

  val mqPropertiesBuilder: BasicProperties.Builder = new BasicProperties.Builder().contentType("application/json")

  val publish = BasicPublish(
    "myExchange",
    noRoutingKey,
    mqPropertiesBuilder.headers(headers.asJava).build(),
    """{"key", :value"}""".getBytes("UTF-8")
  )

  rabbitActor ! RegisterPublisher(myPublishingActor, declarations = Nil, publisherConfirms = true)

  rabbitActor ! RegisterConsumer("myQueue", myConsumingActor, List.empty[Declaration])

  myPublishingActor ? publish onComplete {
    case Success(msg) =>
      logger.info(s"Message successfully published - $msg")
    case Failure(th) =>
      logger.error("Oops", th)
  }
}
