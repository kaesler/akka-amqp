package akka.amqp

import scala.collection.JavaConverters._
import java.io.IOException

object Queue {

  //  private def ioException(declare : RabbitChannel => RabbitQueue.DeclareOk)(channel: RabbitChannel) : Either[IOException,akka.amqp.DeclaredQueue] = {
  //     try {
  //      Right(declare(channel))
  //    } catch {
  //      case ex: IOException => Left(ex)
  //    }
  //  }  
  //  private def ioException(declare: RabbitChannel ⇒ RabbitQueue.DeclareOk)(channel: RabbitChannel): akka.amqp.DeclaredQueue =
  //    declare(channel)

  /**
   * get the default queue
   */
  def apply() = UndeclaredDefaultQueue

  def apply(name: String) = new QueueDeclarationMode(name)
  def named(name: String) = new QueueDeclarationMode(name)
  /**
   * get the default Queue
   */
  def default = UndeclaredDefaultQueue

}

class QueueDeclarationMode private[amqp] (val name: String) {
  def active(durable: Boolean = false, exclusive: Boolean = false, autoDelete: Boolean = true,
             arguments: Option[Map[String, AnyRef]] = None) = ActiveUndeclaredQueue(name, durable, exclusive, autoDelete, arguments)
  def passive = PassiveUndeclaredQueue(name)
}
trait Queue {
  def nameOption: Option[String]
  def params: Option[QueueParams]
}
trait UndeclaredQueue extends Declarable[DeclaredQueue] { queue: Queue ⇒
  def declare(implicit channel: RabbitChannel): DeclaredQueue
}

trait Declarable[T] {
  def declare(implicit channel: RabbitChannel): T
}

case class QueueParams(durable: Boolean, exclusive: Boolean, autoDelete: Boolean, arguments: Option[Map[String, AnyRef]])
case class ActiveUndeclaredQueue private[amqp] (name: String, durable: Boolean, exclusive: Boolean, autoDelete: Boolean, arguments: Option[Map[String, AnyRef]])
  extends Queue with UndeclaredQueue {
  def nameOption = Some(name)
  def params = Some(QueueParams(durable, exclusive, autoDelete, arguments))
  def declare(implicit channel: RabbitChannel): DeclaredQueue = DeclaredQueue(channel.queueDeclare(name, durable, exclusive, autoDelete, arguments.map(_.asJava).getOrElse(null)), params)
}
case class PassiveUndeclaredQueue private[amqp] (name: String)
  extends Queue with UndeclaredQueue {
  def nameOption = Some(name)
  def params = None
  def declare(implicit channel: RabbitChannel): DeclaredQueue = DeclaredQueue(channel.queueDeclarePassive(name), params)
}

case object UndeclaredDefaultQueue extends Queue with UndeclaredQueue {
  def nameOption = None
  def params = None
  def declare(implicit channel: RabbitChannel): DeclaredQueue = DeclaredQueue(channel.queueDeclare(), params)
}

case class DeclaredQueue(peer: RabbitQueue.DeclareOk, params: Option[QueueParams]) extends Queue {
  def name: String = peer.getQueue()
  def nameOption = Some(name)
  def messageCount = peer.getMessageCount()
  def consumerCount = peer.getConsumerCount()

  def purge(implicit channel: RabbitChannel) {
    channel.queuePurge(name)
  }

  def delete(ifUnused: Boolean, ifEmpty: Boolean)(implicit channel: RabbitChannel) {
    channel.queueDelete(name, ifUnused, ifEmpty)
  }

  /**
   * DSL to bind this Queue to an Exchange
   */
  def <<(exchange: DeclaredExchange) = QueueBinding0(exchange, this)

}

trait QueueBinding extends Declarable[RabbitQueue.BindOk] {
  def exchange: Exchange
  def queue: Queue
  def routingKey: String

  def declaredExchange(implicit channel: RabbitChannel): DeclaredExchange = {
    exchange match {
      case ed: UndeclaredExchange             ⇒ ed.declare
      case declaredExchange: DeclaredExchange ⇒ declaredExchange
    }
  }

  def declaredQueue(implicit channel: RabbitChannel): DeclaredQueue = {
    queue match {
      case ed: UndeclaredQueue          ⇒ ed.declare
      case declaredQueue: DeclaredQueue ⇒ declaredQueue
    }
  }

  /**
   * Will bind a Queue to an Exchange. If the given Queue or Exchange has not yet been declared
   * ,it is a Declaration, then that Queue or Exchange will be declared.
   */
  def declare(implicit channel: RabbitChannel): RabbitQueue.BindOk
}

case class QueueBinding0(exchange: Exchange, queue: Queue) {
  def :=(routingKey: String) = QueueBinding1(exchange, queue, routingKey)
}

case class QueueBinding1(exchange: Exchange, queue: Queue, routingKey: String) extends QueueBinding {
  def declare(implicit channel: RabbitChannel) = channel.queueBind(declaredQueue.name, declaredExchange.name, routingKey, null)
  def args(arguments: (String, AnyRef)*) = QueueBinding2(exchange, queue, routingKey, arguments: _*)
}

case class QueueBinding2(exchange: Exchange, queue: Queue, routingKey: String, arguments: (String, AnyRef)*) extends QueueBinding {
  def declare(implicit channel: RabbitChannel) =
    channel.queueBind(declaredQueue.name, declaredExchange.name, routingKey, arguments.toMap.asJava)
}