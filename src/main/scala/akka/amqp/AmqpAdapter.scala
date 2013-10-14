package akka.amqp

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.agent.Agent
import akka.actor.Props
import akka.pattern.ask
import akka.actor.ActorRef
import scala.concurrent.Future
import scala.reflect.ClassTag
import com.rabbitmq.client.Channel

/**
 * Allows use AMQP without necessitating an Akka extension, which requires
 * initialization from the config file.
 */
class AmqpAdapter(settings: AmqpSettings, implicit val _system: ActorSystem) {

  protected val connectionStatusAgent = Agent(false)(_system)
  def isConnected = connectionStatusAgent.get

  val connectionActor = _system.actorOf(Props(new ConnectionActor(settings, connectionStatusAgent)), "amqp-connection")

  def createChannel(): Future[ActorRef] = {
    implicit val to = akka.util.Timeout(5.seconds)
    (connectionActor ? CreateChannel()).mapTo[ActorRef]
  }

  def withTempChannel[T: ClassTag](callback: RabbitChannel ⇒ T): Future[T] = {
    implicit val to = akka.util.Timeout(5.seconds)
    val f = connectionActor ? WithConnection { connection =>
      var channel: Channel = null
      try {
        channel = connection.createChannel
        callback(channel)
      } finally {
        channel.close
      }
    }
    f.mapTo[T]
  }
}
