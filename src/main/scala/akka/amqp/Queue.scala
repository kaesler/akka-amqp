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
  def isUndeclaredDefaultQueue = nameOption.isEmpty
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

trait QueueBinding extends Declarable[DeclaredQueueBinding] {
  def exchange: Exchange
  def queue: Queue
  def routingKey: String
  def getArgs: Option[Seq[(String, AnyRef)]]
  protected def declaredExchange(implicit channel: RabbitChannel): DeclaredExchange = {
    exchange match {
      case ed: UndeclaredExchange             ⇒ ed.declare
      case declaredExchange: DeclaredExchange ⇒ declaredExchange
    }
  }

  protected def declaredQueue(implicit channel: RabbitChannel): DeclaredQueue = {
    queue match {
      case ed: UndeclaredQueue          ⇒ ed.declare
      case declaredQueue: DeclaredQueue ⇒ declaredQueue
    }
  }

  protected def doDeclare(arguments: Option[Seq[(String, AnyRef)]])(implicit channel: RabbitChannel): DeclaredQueueBinding = {
    val q = declaredQueue
    val e = declaredExchange
    if (e.name != "") { //do not queueBind when using the nameless Exchange
      val args = arguments.map(_.toMap.asJava)
      val ok = channel.queueBind(q.name, e.name, routingKey, args.getOrElse(null))
      DeclaredQueueBinding(Some(ok), q, e)
    } else DeclaredQueueBinding(None, q, e)

  }

  /**
   * Will bind a Queue to an Exchange (if not using nameless Exchange).
   * If the given Queue or Exchange has not yet been declared then that Queue or Exchange will be declared.
   */
  def declare(implicit channel: RabbitChannel): DeclaredQueueBinding
}

case class DeclaredQueueBinding(ok: Option[RabbitQueue.BindOk], queue: DeclaredQueue, exchange: DeclaredExchange)

case class QueueBinding0(exchange: Exchange, queue: Queue) extends QueueBinding {
  def getArgs = None
  def routingKey = ""
  def :=(routingKey: String) = QueueBinding1(exchange, queue, routingKey)
  def declare(implicit channel: RabbitChannel) = doDeclare(None)
}

case class QueueBinding1(exchange: Exchange, queue: Queue, routingKey: String) extends QueueBinding {
  def declare(implicit channel: RabbitChannel) = doDeclare(None)
  def getArgs = None
  def args(arguments: (String, AnyRef)*) = QueueBinding2(exchange, queue, routingKey, arguments: _*)
}

case class QueueBinding2(exchange: Exchange, queue: Queue, routingKey: String, arguments: (String, AnyRef)*) extends QueueBinding {
  def getArgs = Some(arguments)
  def declare(implicit channel: RabbitChannel) = doDeclare(Some(arguments))

}