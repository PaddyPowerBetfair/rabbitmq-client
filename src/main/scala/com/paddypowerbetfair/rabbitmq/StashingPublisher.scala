package com.paddypowerbetfair.rabbitmq

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash, Terminated}

/**
 * This is one possible implementation of a publisher that has the limitation of
 * storing many messages on memory during a rabbitMq downtime, the stash queue size
 * should be appropriately configured on akka to avoid OutOfMemoryErrors.
 * Other more sophisticated approaches with back preassure are possible by implementing 
 * your own actor as long as it is able to receive PublisherChannelActor messages.
 */
class StashingPublisher extends Actor with ActorLogging with Stash {

  def receive: Receive = disconnected

  def disconnected: Receive = {
    case PublisherChannelActor(actorRef) =>
      unstashAll()
      switchChannel(actorRef)
    case message: BasicPublish =>
      stash()
    case Terminated(_) =>
  }

  def connected(channelActor: ActorRef): Receive = {
    case PublisherChannelActor(actorRef) =>
      context.unwatch(channelActor)
      switchChannel(actorRef)
    case message: BasicPublish =>
      channelActor.forward(message)
    case Terminated(terminatedChannelActor) =>
      if (channelActor == terminatedChannelActor) {
        log.info("Switching publisher to disconnected mode")
        context.become(disconnected)
      } else {
        log.info("A channel was closed but publisher was no longer using it")
      }
  }

  private def switchChannel(channelActor: ActorRef): Unit = {
    log.info("Switching publisher to a new channel")
    context.watch(channelActor)
    context.become(connected(channelActor))
  }
}

object StashingPublisher {
  def props: Props = Props(new StashingPublisher)
}
