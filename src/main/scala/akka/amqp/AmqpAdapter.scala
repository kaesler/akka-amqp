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
class AmqpAdapter(settings: AmqpSettings, implicit val system: ActorSystem) {

  import system.dispatcher

  protected val connectionStatusAgent = Agent(false)

  /**
   * Return whether the Rabbit connection is established.
   */
  def isConnected = connectionStatusAgent.get

  /**
   * The connection actor to be use to obtain other resources.
   */
  val connectionActor =
    system.actorOf(
      Props(
        new ConnectionActor(settings,
          connectionStatusAgent)),
      "amqp-connection")

  /** Establish the connection to RabbitMq. */
  def connect() = connectionActor ! Connect

  /** Create a channel actor. */
  def createChannel(): Future[ActorRef] = {
    implicit val to = akka.util.Timeout(5.seconds)
    (connectionActor ? CreateChannel()).mapTo[ActorRef]
  }

  /**
   * Perform an operation using a temporary channel.
   * @param callback the function to apply to the channel.
   * @return the callback's result in a Future.
   */
  def withTempChannel[T: ClassTag](callback: RabbitChannel ⇒ T): Future[T] = {
    implicit val to = akka.util.Timeout(60.seconds)
    val f = connectionActor ? WithConnection { connection ⇒
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
