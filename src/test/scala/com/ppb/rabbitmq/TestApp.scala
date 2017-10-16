package com.ppb.rabbitmq

import akka.actor.{Actor, ActorSystem, Props, actorRef2Scala}
import akka.pattern.ask
import akka.util.Timeout
import com.rabbitmq.client.{Address, ConnectionFactory, MessageProperties}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

// scalastyle:off
object TestApp extends App {
  val system = ActorSystem("test", ConfigFactory.load())

  val factory = new ConnectionFactory();
  factory.setUsername("guest");
  factory.setPassword("guest");
  factory.setVirtualHost("/");

  val connection = system.actorOf(Props(classOf[RabbitMqActor], factory, List(new Address("127.0.0.1", 5672))), "rabbitmq-actor")

  val declaration = List(
    ExchangeDeclare("test1", "fanout", true, false),
    ExchangeDeclare("test2", "fanout", true, false),
    ExchangeBind("test2", "test1", ""),
    QueueDeclare("test", true, false, false),
    QueueBind("test", "test2", ""),
    BasicQos(500))

  val consumer = system.actorOf(Props(new Actor() {
    var start = System.currentTimeMillis()
    var i = 0

    def receive = {
      case Delivery(_, _, _, body) =>
        if (i % 1000 == 0) {
          val end = System.currentTimeMillis()
          println(i.toString() + ": " + (end - start))
          start = end
        }
        i = 1 + i
        sender ! Ack
    }
  }), "consumer-actor")

  implicit val timeout = Timeout(2.seconds)
  implicit val ec = system.dispatcher

  connection ? RegisterConsumer("test", consumer, declaration)

  System.in.read()
  system.terminate() onComplete ( _ => () )
  println("C'est finit")
}

object TestProducer extends App {
  val system = ActorSystem("test", ConfigFactory.load())
  import system.dispatcher
  implicit val timeout = Timeout(2.seconds)

  val factory = new ConnectionFactory();
  factory.setUsername("guest");
  factory.setPassword("guest");
  factory.setVirtualHost("/");

  val connection = system.actorOf(Props(classOf[RabbitMqActor], factory, List(new Address("127.0.0.1", 5672))), "rabbitmq-actor")

  val producer = system.actorOf(Props(classOf[StashingPublisher]))

  connection ? RegisterPublisher(producer, List(ExchangeDeclare("test1", "fanout", true, false)))

  while (true) {
    (producer ? BasicPublish("test1", "", MessageProperties.TEXT_PLAIN, "test".getBytes)).map { println(_) }
    Thread.sleep(1000)
  }

}

object TestConfirmProducer extends App {
  val system = ActorSystem("test", ConfigFactory.load())
  import system.dispatcher
  implicit val timeout = Timeout(2.seconds)

  val factory = new ConnectionFactory();
  factory.setUsername("guest");
  factory.setPassword("guest");
  factory.setVirtualHost("/");

  val connection = system.actorOf(Props(classOf[RabbitMqActor], factory, List(new Address("127.0.0.1", 5672))), "rabbitmq-actor")

  val producer = system.actorOf(Props(classOf[StashingPublisher]))

  connection ? RegisterPublisher(producer, List(ExchangeDeclare("test1", "fanout", true, false)), true)

  while (true) {
    (producer ? BasicPublish("test1", "", MessageProperties.PERSISTENT_TEXT_PLAIN, "test".getBytes)).map { println(_) }
    Thread.sleep(1000)
  }

}
// scalastyle:on
