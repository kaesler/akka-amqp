package akka.amqp

import collection.mutable.ArrayBuffer
import akka.actor.FSM.SubscribeTransitionCallBack
import java.io.IOException
import util.control.Exception
import akka.actor._
import akka.util.Timeout
import akka.event.Logging
import akka.pattern.ask
import akka.serialization.SerializationExtension
import akka.amqp.Message._
import scala.concurrent.util.duration._
import scala.concurrent.util.Duration
import scala.concurrent.{ Await, Future }
import scala.concurrent.{ ExecutionContext, Promise }
import reflect.ClassTag
import akka.serialization.SerializationExtension
import akka.actor.Props
import akka.actor.Stash

private sealed trait ChannelState
private case object Available extends ChannelState
private case object Initializing extends ChannelState
private case object Unavailable extends ChannelState

private case class RequestChannel(connection: RabbitConnection)

/**
 * Stores mess
 */
trait DowntimeStash extends Stash {
  this: DurableChannelActor ⇒

  when(Unavailable) {
    case Event(channelCallback: OnlyIfAvailable, _) ⇒ //the downtime stash will stash the message until available
      stash()
      stay()
  }

  onTransition {
    case (_, Available) ⇒
      unstashAll()
  }
}

private[amqp] class DurableChannelActor
  extends Actor with FSM[ChannelState, Option[RabbitChannel]] with ShutdownListener {

  //perhaps registered callbacks should be replaced with the akka.actor.Stash
  val registeredCallbacks = new collection.mutable.Queue[RabbitChannel ⇒ Unit]
  val settings = AmqpExtension(context.system).settings
  val serialization = SerializationExtension(context.system)

  startWith(Unavailable, None)

  def publishToExchange(pub: PublishToExchange, channel: RabbitChannel): Option[Long] = {
    import pub._
    log.debug("Publishing confirmed on '{}': {}", exchangeName, message)
    import message._
    val s = serialization.findSerializerFor(payload)
    val serialized = s.toBinary(payload)
    if (confirm) {
      val seqNo = channel.getNextPublishSeqNo
      channel.basicPublish(exchangeName, routingKey, mandatory, immediate, properties.getOrElse(null), serialized)
      Some(seqNo)
    } else {
      channel.basicPublish(exchangeName, routingKey, mandatory, immediate, properties.getOrElse(null), serialized)
      None
    }
  }

  when(Unavailable) {
    case Event(RequestChannel(connection), _) ⇒
      cancelTimer("request-channel")
      log.debug("Requesting channel from {}", connection)
      try {
        self ! connection.createChannel
        stay()
      } catch {
        case ioe: IOException ⇒
          log.error(ioe, "Error while requesting channel from connection {}", connection)
          setTimer("request-channel", RequestChannel(connection), settings.channelReconnectTimeout, true)
      }
    case Event(ConnectionConnected(connection), _) ⇒
      connection ! WithConnection(c ⇒ self ! RequestChannel(c))
      stay()
    case Event(channel: RabbitChannel, _) ⇒
      log.debug("Received channel {}", channel)
      channel.addShutdownListener(this)
      registeredCallbacks.foreach(_.apply(channel))
      goto(Available) using Some(channel)
    case Event(cause: ShutdownSignalException, _) ⇒
      handleShutdown(cause)
    case Event(WhenAvailable(callback), _) ⇒
      registeredCallbacks += callback
      stay()
    case Event(mess: PublishToExchange, _) ⇒
      val send = sender

      registeredCallbacks += { channel: RabbitChannel ⇒
        publishToExchange(mess, channel) match {
          case Some(seqNo) ⇒ send ! seqNo
          case None        ⇒
        }
      }
      stay()
    case Event(WithChannel(callback), _) ⇒
      val send = sender //make it so the closure, can capture the sender

      registeredCallbacks += { rc: RabbitChannel ⇒ send ! callback(rc) }
      stay()
    case Event(OnlyIfAvailable(callback), _) ⇒
      //not available, dont execute
      stay()
  }

  when(Available) {
    case Event(mess: PublishToExchange, Some(channel)) ⇒
      publishToExchange(mess, channel) match {
        case Some(seqNo) ⇒ stay() replying seqNo
        case None        ⇒ stay()
      }
    case Event(ConnectionDisconnected(), Some(channel)) ⇒
      log.warning("Connection went down of channel {}", channel)
      goto(Unavailable) using None
    case Event(OnlyIfAvailable(callback), Some(channel)) ⇒
      callback.apply(channel)
      stay()
    case Event(WithChannel(callback), Some(channel)) ⇒
      stay() replying callback.apply(channel)
    case Event(cause: ShutdownSignalException, _) ⇒
      handleShutdown(cause)
    case Event(WhenAvailable(callback), channel) ⇒
      channel.foreach(callback.apply(_))
      stay()
  }

  def handleShutdown(cause: ShutdownSignalException): State = {
    if (cause.isHardError) { // connection error, await ConnectionDisconnected()
      stay()
    } else { // channel error
      val channel = cause.getReference.asInstanceOf[RabbitChannel]
      if (cause.isInitiatedByApplication) {
        log.debug("Channel {} shutdown ({})", channel, cause.getMessage)
        stop()
      } else {
        log.error(cause, "Channel {} broke down", channel)
        setTimer("request-channel", RequestChannel(channel.getConnection), settings.channelReconnectTimeout, true)
        goto(Unavailable) using None
      }
    }
  }

  def shutdownCompleted(cause: ShutdownSignalException) {
    self ! cause
  }
}

/**
 * Message to Execute the given code when the Channel is first Received from the ConnectionActor
 * Or immediately if the channel has already been received
 */
case class WhenAvailable(callback: RabbitChannel ⇒ Unit)

/**
 * Will execute only if the channel is currently available. Otherwise the message will be dropped.
 */
case class OnlyIfAvailable(callback: RabbitChannel ⇒ Unit)
/**
 * Message to Execute the given code when the Channel is first Received from the ConnectionActor
 * Or immediately if the channel has already been received
 */
case class WithChannel[T](callback: RabbitChannel ⇒ T)

//trait HasDurableChannel {
//  abstract class DurableChannel private[amqp]()(implicit protected val extension: AmqpExtensionImpl) extends akka.pattern.AskSupport {
//    val persistentChannel: Boolean
//
//    private val log = Logging(system, this.getClass)
//    val settings = AmqpExtension(system).settings
//
//    def channelActorCreator = if (persistentChannel) {
//      Props(new DurableChannelActor with DowntimeStash).withDispatcher("akka.amqp.stashing-dispatcher")
//    } else {
//      Props(new DurableChannelActor)
//    }
//
//    private[amqp] val connectionActor : ActorRef = connection.durableConnectionActor
//    implicit val timeout = Timeout(settings.channelCreationTimeout)
//    
//    // copy from internals, so at lease channel actors are children of the connection for supervision purposes
//    val channelFuture = connectionActor.?(CreateRandomNameChild(channelActorCreator)) mapTo reflect.classTag[ActorRef]
//    private[amqp] val channelActor = Await.result(channelFuture, settings.channelCreationTimeout)
//
//    connectionActor ! SubscribeTransitionCallBack(channelActor)
//
//    /**
//     * Executes the given code when the Channel is first Received from the ConnectionActor
//     * Or immediately if the channel has already been received
//     * Cannot time out
//     */
//    @deprecated("use the tell method instead","10.16.2012")
//    def whenAvailable(callback: RabbitChannel ⇒ Unit) {
//      tell(callback)
//    }
//    
//    /**
//     * Executes the given code when the Channel is first Received from the ConnectionActor
//     * Or immediately if the channel has already been received
//     * Cannot time out
//     */
//    def tell(callback: RabbitChannel ⇒ Unit) {
//      channelActor ! WhenAvailable(callback)
//    }
//    
//    def ! = tell _
//    
//  //  def ?[T : ClassTag](callback: RabbitChannel ⇒ T) = ask(callback)
//
//     /**
//     * Executes the given code when the Channel is first Received from the ConnectionActor
//     * Or immediately if the channel has already been received
//     * Will timeout using AmqpSettings.interactionTimeout
//     */
//    def ask[T: ClassTag](callback: RabbitChannel ⇒ T): Future[T] = {
//import ExecutionContext.Implicits.global
//      implicit val timeout = Timeout(settings.interactionTimeout)
//      (channelActor.ask(WithChannel(callback))).mapTo[T]
//      
//    }
//    /**
//     * Executes the given code when the Channel is first Received from the ConnectionActor
//     * Or immediately if the channel has already been received
//     * Will timeout using AmqpSettings.interactionTimeout
//     */
//      @deprecated("use the ask method instead", "10.16.2012")
//    def withChannel[T: reflect.ClassTag](callback: RabbitChannel ⇒ T): Future[T] = 
//        ask(callback)
//    
//
//    def stop() {
//      if (!channelActor.isTerminated) {
//        channelActor ! OnlyIfAvailable { channel ⇒
//          if (channel.isOpen) {
//            log.debug("Closing channel {}", channel)
//            Exception.ignoring(classOf[AlreadyClosedException], classOf[ShutdownSignalException]) {
//              channel.close()
//            }
//          }
//        }
//        channelActor ! PoisonPill
//      }
//    }
//  }
//}