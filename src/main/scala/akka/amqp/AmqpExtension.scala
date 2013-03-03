package akka.amqp
import akka.pattern.ask
import scala.language.implicitConversions
import akka.actor._
import com.typesafe.config.Config
import akka.actor.ExtensionIdProvider
import akka.actor.ExtensionId
import akka.actor.ExtendedActorSystem
import akka.actor.ActorSystem
import scala.concurrent.Future
import scala.concurrent.Await
import reflect.ClassTag
import akka.agent.Agent
import akka.pattern.ask
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

object AmqpExtension extends ExtensionId[AmqpExtensionImpl] with ExtensionIdProvider {

  override def lookup() = this
  override def createExtension(system: ExtendedActorSystem): AmqpExtensionImpl = new AmqpExtensionImpl()(system)
}

class AmqpExtensionImpl(implicit val _system: ActorSystem) extends Extension {
  implicit val settings = new AmqpSettings(_system.settings.config.getConfig("akka.amqp.default"))
  implicit val extension = this

  protected val connectionStatusAgent = Agent(false)
  def isConnected = connectionStatusAgent.get

  val connectionActor = _system.actorOf(Props(new ConnectionActor(settings, connectionStatusAgent)), "amqp-connection")

  def createChannel = {
    implicit val to = akka.util.Timeout(5 seconds)
    (connectionActor ? CreateChannel()).mapTo[ActorRef]
  }

  //private implicit val timeout = akka.util.Timeout(settings.interactionTimeout)

  def withTempChannel[T: ClassTag](callback: RabbitChannel ⇒ T): Future[T] = {
    ???
    //    withConnection { conn ⇒
    //      val ch = conn.createChannel()
    //      try {
    //        callback(ch)
    //      } finally {
    //        if (ch.isOpen) { ch.close() }
    //      }
    //    }
  }

}

class AmqpSettings(config: Config) {
  import scala.concurrent.duration
  import scala.collection.JavaConverters._
  //durationIn
  val addresses: Seq[String] = config.getStringList("addresses").asScala.toSeq
  val user: String = config.getString("user")
  val pass: String = config.getString("pass")
  val vhost: String = config.getString("vhost")
  val amqpHeartbeat: FiniteDuration = DurationLong(config.getMilliseconds("heartbeat")).milli
  val maxReconnectDelay: Duration = DurationLong(config.getMilliseconds("max-reconnect-delay")).milli
  val channelThreads: Int = config.getInt("channel-threads")
  val interactionTimeout: Duration = DurationLong(config.getMilliseconds("interaction-timeout")).milli
  val channelCreationTimeout: Duration = DurationLong(config.getMilliseconds("channel-creation-timeout")).milli
  val channelReconnectTimeout: Duration = DurationLong(config.getMilliseconds("channel-reconnect-timeout")).milli
  val publisherConfirmTimeout: FiniteDuration = DurationLong(config.getMilliseconds("publisher-confirm-timeout")).milli
}

