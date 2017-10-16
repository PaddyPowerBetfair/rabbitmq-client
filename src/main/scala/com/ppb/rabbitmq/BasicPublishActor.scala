package com.ppb.rabbitmq

import com.rabbitmq.client.Connection
import com.rabbitmq.client.AMQP
import akka.actor.ActorRef
import scala.util.control.NonFatal

//Channel publisher accepted messages
case class BasicPublish(exchange: String, routingKey: String, props: AMQP.BasicProperties, body: Array[Byte])

//Channel publisher outgoing messages 
case class PublisherChannelActor(actorRef: ActorRef)

class BasicPublishActor(connection: Connection, registration: RegisterPublisher) extends ChannelActor(connection) with DeclarationAdapter {

  def receive: Receive = {
    case BasicPublish(exchange, routingKey, props, body) =>
      try {
        channel.basicPublish(exchange, routingKey, false, false, props, body)
        sender ! Ack
      } catch {
        case NonFatal(e) => 
          sender ! Nack
          throw e
      }
    case declaration: Declaration =>
      declare(declaration)
  }

  registration.declarations.foreach { declare }
  registration.publisher ! PublisherChannelActor(self)

}