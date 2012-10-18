package akka.amqp

import scala.collection.JavaConverters._
import java.io.IOException

trait Queue

object Queue {

  //  private def ioException(declare : RabbitChannel => RabbitQueue.DeclareOk)(channel: RabbitChannel) : Either[IOException,akka.amqp.DeclaredQueue] = {
  //     try {
  //      Right(declare(channel))
  //    } catch {
  //      case ex: IOException => Left(ex)
  //    }
  //  }  
  private def ioException(declare: RabbitChannel ⇒ RabbitQueue.DeclareOk)(channel: RabbitChannel): akka.amqp.DeclaredQueue =
    declare(channel)

  /**
   * get the default queue
   */
  def apply(): QueueDeclaration = ioException(_.queueDeclare()) _

  def apply(name: String, durable: Boolean = false, exclusive: Boolean = false, autoDelete: Boolean = true,
            arguments: Option[Map[String, AnyRef]] = None): QueueDeclaration = ioException {
    _.queueDeclare(name, durable, exclusive, autoDelete, arguments.map(_.asJava).getOrElse(null))
  } _

  /**
   * get the default Queue
   */
  def default = apply()
  def passive(name: String): QueueDeclaration = ioException(_.queueDeclarePassive(name)) _

}

trait QueueBinding {
  def exchange: DeclaredExchange
  def queueName: String
  def routingKey: String
  def bind(implicit channel: RabbitChannel): RabbitQueue.BindOk
}
case class QueueBinding0(exchange: DeclaredExchange, queueName: String) {
  def :=(routingKey: String) = QueueBinding1(exchange, queueName, routingKey)
}

case class QueueBinding1(exchange: DeclaredExchange, queueName: String, routingKey: String) extends QueueBinding {
  def bind(implicit channel: RabbitChannel) = channel.queueBind(queueName, exchange.name, routingKey, null)
  def args(arguments: (String, AnyRef)*) = QueueBinding2(exchange, queueName, routingKey, arguments: _*)
}

case class QueueBinding2(exchange: DeclaredExchange, queueName: String, routingKey: String, arguments: (String, AnyRef)*) extends QueueBinding {
  def bind(implicit channel: RabbitChannel) =
    channel.queueBind(queueName, exchange.name, routingKey, arguments.toMap.asJava)
}