# RabbitMq-Client [![Build Status](https://travis-ci.org/PaddyPowerBetfair/rabbitmq-client.svg?branch=master)](https://travis-ci.org/PaddyPowerBetfair/rabbitmq-client) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/1af5197636824cf78ef5de598ca01e77)](https://www.codacy.com/app/rodoherty1/rabbitmq-client?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=PaddyPowerBetfair/rabbitmq-client&amp;utm_campaign=Badge_Grade) [![Coverage Status](https://coveralls.io/repos/github/PaddyPowerBetfair/rabbitmq-client/badge.svg?branch=master)](https://coveralls.io/github/PaddyPowerBetfair/rabbitmq-client?branch=master)

`rabbitmq-client` is an Akka actor-based wrapper for the standard java RabbitMQ API.

There are two basic use-cases; publish and consume.

## How to publish

```scala
object MyApp extends App {
  val addresses: List[com.rabbitmq.client.Address] = ???

  // Use constructor or setters to apply connection details of your rabbit broker.
  val connectionFactory = new com.rabbitmq.client.ConnectionFactory()

  val rabbitActor = actorSystem.actorOf(Props(classOf[RabbitMqActor], connectionFactory, addresses), "my-rabbitmq-actor")

  val myPublishingActor = system.actorOf(MyPublishingActor.props())

  rabbitActor ! RegisterPublisher(myPublishingActor, declarations = Nil, publisherConfirms = false)
}
```

```myPublishingActor``` will now receive a ```PublisherChannelActor``` which contains the ```ActorRef``` that must be used by ```myPublishingActor``` when it wants to publish to RabbitMq.

```scala
class MyPublishingActor extends Actor {
  def receive: Receive = {
    case PublisherChannelActor(rabbitPublisher) => context.become(readyToPublish(rabbitPublisher))
  }

  def readyToPublish(rabbitPublisher: ActorRef): Receive = {
    case myJsonMessage: String =>
      rabbitPublisher !
    BasicPublish(
      "exchangeName",
      "routingKey",
      myBasicProperties, // reference to BasicProperties which defines AMQP Headers
      myJsonMessage.getBytes("UTF-8")
    )
  }
}
```

## How to consume

Below is a very basic sketch of an application that will consume from a RabbitMq Queue.

```scala
object BasicSampleApp extends App {
  val addresses: List[com.rabbitmq.client.Address] = ???
  val connectionFactory = new com.rabbitmq.client.ConnectionFactory()

  val rabbitActor: ActorRef = system.actorOf(Props(new RabbitMqActor(connectionFactory, addresses)), "rabbitActor")

  val myConsumingActor: ActorRef = system.actorOf(Props(new MyConsumingActor()))

  rabbitActor ! RegisterConsumer("myQueue", myConsumingActor, List.empty[Declaration])
}

class MyConsumingActor extends Actor {
  override def receive: Receive = {
    case d: Delivery =>
      log.info(s"Message successfully consumed - ${d.body.map(_.toChar).mkString}")
      sender() ! Ack
  }
}
```

## How can I contribute?
Please see [CONTRIBUTING.md](CONTRIBUTING.md).

## What licence is this released under?
This is released under a modified version of the BSD licence.
Please see [LICENCE.md](https://github.com/PaddyPowerBetfair/standards/blob/master/LICENCE.md).
