# RabbitMq-Client

This library, written in Scala, is an Akka actor-based wrapper for the standard java RabbitMQ API.Akka actor-based rabbitq client library.

There are two main use-cases; publish and consume.

## How to publish
    
Set up the pre-requisite objects in a class which will eventually request something to be published to a RabbitMq Exchange 
    
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
    case PublisherChannelActor(rabbitPublisher) => ??? // Make a note of this actorRef
  }

...

  // Now you may publish to your RabbitMq Broker as follows:
  rabbitPublisher !
    BasicPublish(
      "exchangeName",
      "routingKey",
      myBasicProperties, // reference to BasicProperties which defines AMQP Headers
      myJsonMessage.getBytes("UTF-8")
    )
}
```        

# How to consume
Coming soon!
