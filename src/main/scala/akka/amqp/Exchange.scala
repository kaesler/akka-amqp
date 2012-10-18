package akka.amqp

import scala.collection.JavaConverters._

object Exchange {
  def nameless = NamelessExchange
  def apply(name: String) = UndeclaredExchange(name)
  def named(name: String) = UndeclaredExchange(name)
}

case class ExchangeParams(exchangeType: String,
                          durable: Boolean = false,
                          autoDelete: Boolean = false,
                          internal: Boolean = false,
                          arguments: Option[Map[String, AnyRef]] = None)

abstract class UndeclaredExchange0(val name: String) {
  def apply(exchangeType: String,
            durable: Boolean = false,
            autoDelete: Boolean = false,
            internal: Boolean = false,
            arguments: Option[Map[String, AnyRef]] = None): NamedExchangeDeclaration = { channel ⇒

    val declareOk = channel.exchangeDeclare(name, exchangeType, durable, autoDelete, internal, arguments.map(_.asJava).getOrElse(null))
    val params = ExchangeParams(exchangeType, durable, autoDelete, internal, arguments)
    NamedExchange(name, declareOk, Some(params))
  }
  def passive: NamedExchangeDeclaration = channel ⇒ NamedExchange(name, channel.exchangeDeclarePassive(name), None)
}
case class UndeclaredExchange(override val name: String) extends UndeclaredExchange0(name)
case object NamelessExchange extends DeclaredExchange {
  val name = ""
  val params = None
}

object DeclaredExchange {
  implicit def declared2Declaration(ex: DeclaredExchange): ExchangeDeclaration = _ ⇒ ex
}

trait DeclaredExchange {
  val name: String
  val params: Option[ExchangeParams]
}

case class NamedExchange(val name: String, val peer: RabbitExchange.DeclareOk, val params: Option[ExchangeParams] = None) extends DeclaredExchange {

  def delete(ifUnused: Boolean)(implicit channel: RabbitChannel) = channel.exchangeDelete(name, ifUnused)

  /**
   * start the binding process to bind this Queue to an Exchange
   */
  def >>(queue: DeclaredQueue) = QueueBinding0(this, queue.name)

}

case class ExchangeToExchangeBinding(destination: DeclaredExchange,
                                     source: DeclaredExchange,
                                     routingKey: String,
                                     arguments: Option[Map[String, AnyRef]] = None) {

  def bind(channel: RabbitChannel) {
    channel.exchangeBind(destination.name, source.name, routingKey, arguments.map(_.asJava).getOrElse(null))
  }

  def unbind(channel: RabbitChannel) {
    channel.exchangeUnbind(destination.name, source.name, routingKey, arguments.map(_.asJava).getOrElse(null))
  }
}
