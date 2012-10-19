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

}

