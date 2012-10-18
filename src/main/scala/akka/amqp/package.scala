package akka

import java.io.IOException

package object amqp {

  object RabbitQueue extends com.rabbitmq.client.AMQP.Queue {
    type DeclareOk = com.rabbitmq.client.AMQP.Queue.DeclareOk
    type BindOk = com.rabbitmq.client.AMQP.Queue.BindOk
  }

  object RabbitExchange extends com.rabbitmq.client.AMQP.Exchange {
    type DeclareOk = com.rabbitmq.client.AMQP.Exchange.DeclareOk
  }

  @deprecated("use ConnectionActor", "")
  type DurableConnectionActor = ConnectionActor

  type ReturnListener = com.rabbitmq.client.ReturnListener
  type ConfirmListener = com.rabbitmq.client.ConfirmListener
  type BasicProperties = com.rabbitmq.client.AMQP.BasicProperties
  type Envelope = com.rabbitmq.client.Envelope
  type DefaultConsumer = com.rabbitmq.client.DefaultConsumer
  type ShutdownListener = com.rabbitmq.client.ShutdownListener
  type ShutdownSignalException = com.rabbitmq.client.ShutdownSignalException
  type AlreadyClosedException = com.rabbitmq.client.AlreadyClosedException
  type ConnectionFactory = com.rabbitmq.client.ConnectionFactory
  type RabbitConnection = com.rabbitmq.client.Connection
  type RabbitChannel = com.rabbitmq.client.Channel

  object RabbitAddress {
    def parseAddress = com.rabbitmq.client.Address.parseAddress _
  }
  class RabbitAddress(host: String, port: Int) extends com.rabbitmq.client.Address(host, port) {
    def this(host: String) = this(host, -1)
  }

  type NamedExchangeDeclaration = RabbitChannel ⇒ NamedExchange
  type ExchangeDeclaration = RabbitChannel ⇒ DeclaredExchange
  type QueueDeclaration = RabbitChannel ⇒ DeclaredQueue

  implicit class DeclaredQueue(val peer: RabbitQueue.DeclareOk) extends Queue {
    def name: String = peer.getQueue()
    def messageCount = peer.getMessageCount()
    def consumerCount = peer.getConsumerCount()

    def purge(implicit channel: RabbitChannel) {
      channel.queuePurge(name)
    }

    def delete(ifUnused: Boolean, ifEmpty: Boolean)(implicit channel: RabbitChannel) {
      channel.queueDelete(name, ifUnused, ifEmpty)
    }

    /**
     * start the binding process to bind this Queue to an Exchange
     */
    def <<(exchange: DeclaredExchange) = QueueBinding0(exchange, name)

  }

}

