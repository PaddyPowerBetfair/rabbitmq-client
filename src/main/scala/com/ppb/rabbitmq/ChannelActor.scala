package com.ppb.rabbitmq

import akka.actor.{Actor, ActorLogging}
import com.rabbitmq.client.Connection

import scala.util.Try

abstract class ChannelActor(connection: Connection) extends Actor with ActorLogging {

  val channel = connection.createChannel()

  override def postStop(): Unit = {
    log.debug("Stopping channel actor")
    Try(channel.close())
  }
}