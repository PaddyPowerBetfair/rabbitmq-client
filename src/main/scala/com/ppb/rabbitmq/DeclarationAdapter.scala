package com.ppb.rabbitmq

sealed trait Declaration
case class ExchangeDeclare(exchange: String, exchangeType: String, durable: Boolean, autoDelete: Boolean, arguments: Map[String, Object] = Map.empty)
  extends Declaration
case class ExchangeBind(destination: String, source: String, routingKey: String, arguments: Map[String, Object] = Map.empty)
  extends Declaration
case class QueueDeclare(queue: String, durable: Boolean, exclusive: Boolean, autoDelete: Boolean, arguments: Map[String, Object] = Map.empty)
  extends Declaration
case class QueueBind(queue: String, exchange: String, routingKey: String, arguments: Map[String, Object] = Map.empty) extends Declaration
case class BasicQos(prefetchCount: Int) extends Declaration

trait DeclarationAdapter {
  self: ChannelActor =>
  import scala.collection.JavaConverters._

  def declare: PartialFunction[Declaration, Unit] = {
    case ExchangeDeclare(exchange, exchangeType, durable, autoDelete, arguments) =>
      channel.exchangeDeclare(exchange, exchangeType, durable, autoDelete, arguments.asJava)
    case ExchangeBind(destination, source, routingKey, arguments) =>
      channel.exchangeBind(destination, source, routingKey)
    case QueueDeclare(queue, durable, exclusive, autoDelete, arguments) =>
      channel.queueDeclare(queue, durable, exclusive, autoDelete, arguments.asJava)
    case QueueBind(queue, exchange, routingKey, arguments) =>
      channel.queueBind(queue, exchange, routingKey, arguments.asJava)
    case BasicQos(prefetchCount) =>
      channel.basicQos(prefetchCount)
  }
}