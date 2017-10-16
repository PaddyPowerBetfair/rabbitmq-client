package com.ppb.rabbitmq

import akka.actor.{Actor, ActorLogging}
import com.rabbitmq.client.Channel

import scala.concurrent.duration.FiniteDuration

class AckActor(channel: Channel, deliveryTag: Long, timeout: FiniteDuration, ackStrategy: AckStrategy) extends Actor with ActorLogging {
  import context.dispatcher

  def receive: Receive = ackOrNack andThen stopActor

  private def ackOrNack: Receive = {
    case Ack =>
      ackStrategy.ack(channel, deliveryTag)
    case Nack =>
      ackStrategy.nack(channel, deliveryTag)
    case Expire =>
      log.warning("Expired message detected {}", deliveryTag)
      ackStrategy.expire(channel, deliveryTag)
  }

  private def stopActor(u: Unit) = {
    context.stop(self)
  }

  val tick = context.system.scheduler.scheduleOnce(timeout, self, Expire)

  override def postStop(): Unit = {
    tick.cancel()
  }
}

trait AckStrategy {
  def ack(channel: Channel, deliveryTag: Long)
  def nack(channel: Channel, deliveryTag: Long)
  def expire(channel: Channel, deliveryTag: Long)
}

class DefaultAckStrategy extends AckStrategy {
  override def ack(channel: Channel, deliveryTag: Long): Unit = {
    channel.basicAck(deliveryTag, false)
  }

  override def nack(channel: Channel, deliveryTag: Long): Unit = {
    channel.basicNack(deliveryTag, false, true)
  }

  override def expire(channel: Channel, deliveryTag: Long): Unit = {
    channel.basicAck(deliveryTag, false)
  }
}