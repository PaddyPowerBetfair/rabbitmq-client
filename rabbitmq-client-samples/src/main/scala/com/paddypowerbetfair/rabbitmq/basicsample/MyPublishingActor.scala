
package com.paddypowerbetfair.rabbitmq.basicsample

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import com.paddypowerbetfair.rabbitmq.{BasicPublish, PublisherChannelActor}

object MyPublishingActor {
  def props: Props = Props(new MyPublishingActor())
}



class MyPublishingActor extends Actor with ActorLogging with Stash {

  def receive: Receive = {
    case PublisherChannelActor(rabbitPublisher) =>
      unstashAll()
      context.become(readyToPublish(rabbitPublisher))

    case _ => stash()
  }


  def readyToPublish(publisher: ActorRef): Receive = {
    case msg: BasicPublish =>
      publisher ! msg
      sender() ! s"Message Published - $msg"
  }

}