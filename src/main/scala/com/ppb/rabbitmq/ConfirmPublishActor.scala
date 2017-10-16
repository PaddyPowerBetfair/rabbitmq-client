package com.ppb.rabbitmq

import akka.actor.ActorRef
import com.rabbitmq.client.{ConfirmListener, Connection}

import scala.collection.SortedMap

class ConfirmPublishActor(connection: Connection, registration: RegisterPublisher) extends BasicPublishActor(connection, registration) {

  var inFlight = SortedMap[Long, ActorRef]()

  override def receive: Receive = {
    case BasicPublish(exchange, routingKey, props, body) =>
      val nextSeqNo = channel.getNextPublishSeqNo()
      inFlight += (nextSeqNo -> sender)
      channel.basicPublish(exchange, routingKey, false, false, props, body)
    case declaration: Declaration =>
      declare(declaration)
    case ConfirmAckNack(deliveryTag, true, ackOrNAck) =>
      for ((_, sender) <- inFlight.to(deliveryTag)) sender ! ackOrNAck
      inFlight = inFlight.from(deliveryTag + 1)
    case ConfirmAckNack(deliveryTag, false, ackOrNAck) =>
      inFlight(deliveryTag) ! ackOrNAck
      inFlight -= deliveryTag
  }

  channel.confirmSelect()

  case class ConfirmAckNack(deliveryTag: Long, multiple: Boolean, ackOrNack: AckOrNack)
  channel.addConfirmListener(new ConfirmListener() {
    def handleAck(deliveryTag: Long, multiple: Boolean): Unit = {
      self ! ConfirmAckNack(deliveryTag, multiple, Ack)
    }
    def handleNack(deliveryTag: Long, multiple: Boolean): Unit = {
      self ! ConfirmAckNack(deliveryTag, multiple, Nack)
    }
  })

  override def postStop(): Unit = {
    super.postStop()
    for ((_, sender) <- inFlight) sender ! Nack
  }
}
