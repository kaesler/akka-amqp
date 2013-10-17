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
object AmqpExtension extends ExtensionId[AmqpExtensionImpl] with ExtensionIdProvider {

  override def lookup() = this
  override def createExtension(system: ExtendedActorSystem): AmqpExtensionImpl = new AmqpExtensionImpl()(system)
}

class AmqpExtensionImpl(implicit val _system: ActorSystem) extends Extension {
  implicit val settings = AmqpSettings(_system.settings.config.getConfig("akka.amqp.default"))
  implicit val extension = this

  protected val connectionStatusAgent = Agent(false)(_system.dispatcher)
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

