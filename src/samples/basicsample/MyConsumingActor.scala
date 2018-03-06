package com.paddypowerbetfair.rabbitmq.basicsample

import akka.actor.{Actor, ActorLogging, Props}
import com.paddypowerbetfair.rabbitmq.{Ack, Delivery}

object MyConsumingActor {
  def props: Props = Props(new MyConsumingActor())
}

class MyConsumingActor extends Actor with ActorLogging {
  override def receive: Receive = {
    case d: Delivery =>
      log.info(s"Message successfully consumed - ${d.body.map(_.toChar).mkString}")
      sender() ! Ack
      context.system.terminate()
  }
}
