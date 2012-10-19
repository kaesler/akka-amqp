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
import ChannelActor._
import scala.concurrent.util.FiniteDuration
///**
// * Stores mess
// */
trait DowntimeStash extends Stash {
  this: ChannelActor ⇒

  when(Unavailable) {
    case Event(channelCallback: OnlyIfAvailable, _) ⇒ //the downtime stash will stash the message until available
      stash()
      stay()
  }

  onTransition {
    case Unavailable -> Available ⇒ unstashAll()
  }
}
private[amqp] case object RequestNewChannel
object ChannelActor {

  /**
   * ****************************************
   *                 Channel States
   * ****************************************
   */
  sealed trait ChannelState
  case object Unavailable extends ChannelState
  case object Available extends ChannelState
  case class ChannelData(channel: Option[RabbitChannel], mode: ChannelMode)

  object %: {
    def unapply(channelData: ChannelData): Option[(Option[RabbitChannel], ChannelMode)] = {
      Some((channelData.channel, channelData.mode))
    }
  }

  sealed trait ChannelMode {
    def %:(channel: Option[RabbitChannel]): ChannelData = ChannelData(channel, this)
  }
  /**
   * A basicChannel may declare/delete Queues and Exchanges.
   * It may also publish but will not send back Returned or Confirm messages
   */
  case object BasicChannel extends ChannelMode

  /**
   * The given listening ActorRef receives messages that are Returned or Confirmed
   */
  case class Publisher(listener: ActorRef) extends ChannelMode
  /**
   * The given listening ActorRef receives messages that are sent to the queue.
   */
  case class Consumer(listener: ActorRef, queue: DeclaredQueue) extends ChannelMode

  /**
   * ****************************************
   *         Channel Actor Message API
   * ****************************************
   */

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

  case class Declare(exchangeOrQueue: Declarable[_]*)

  case class DeleteQueue(queue: DeclaredQueue, ifUnused: Boolean, ifEmpty: Boolean)
  case class DeleteExchange(exchange: NamedExchange, ifUnused: Boolean)
}

private[amqp] class ChannelActor(settings: AmqpSettings)
  extends Actor with FSM[ChannelState, ChannelData] with ShutdownListener with ChannelPublisher {
  //perhaps registered callbacks should be replaced with the akka.actor.Stash
  val registeredCallbacks = new collection.mutable.Queue[RabbitChannel ⇒ Unit]
  val serialization = SerializationExtension(context.system)

  //  /**
  //   * allow us to use a variant of when that can target multiple states.
  //   */
  //  def whens(stateName: ChannelState*)(stateFunction: StateFunction): Unit = stateName foreach { when(_, null)(stateFunction) }
  //
  //  /**
  //   * use to transition from Available to Unavailable
  //   */
  //  def toUnavailable = stateName match {
  //    case AvailablePublisher             ⇒ UnavailablePublisher
  //    case AvailableConfirmingPublisher   ⇒ UnavailableConfirmingPublisher
  //    case AvailableConsumer              ⇒ UnavailableConsumer
  //    case UnavailablePublisher           ⇒ UnavailablePublisher
  //    case UnavailableConfirmingPublisher ⇒ UnavailableConfirmingPublisher
  //    case UnavailableConsumer            ⇒ UnavailableConsumer
  //  }
  //
  //  /**
  //   * transition from Unavailable to Available
  //   */
  //  def toAvailable = stateName match {
  //    case UnavailablePublisher           ⇒ AvailablePublisher
  //    case UnavailableConfirmingPublisher ⇒ AvailableConfirmingPublisher
  //    case UnavailableConsumer            ⇒ AvailableConsumer
  //    case AvailablePublisher             ⇒ AvailablePublisher
  //    case AvailableConfirmingPublisher   ⇒ AvailableConfirmingPublisher
  //    case AvailableConsumer              ⇒ AvailableConsumer
  //  }

  startWith(Unavailable, ChannelData(None, BasicChannel))

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
    //    case Event(ConnectionConnected(channel), _) ⇒
    //      cancelTimer("request-channel")
    //      log.debug("Requesting channel from {}", connection)
    //      try {
    //        self ! connection.createChannel
    //        stay()
    //      } catch {
    //        case ioe: IOException ⇒
    //          log.error(ioe, "Error while requesting channel from connection {}", connection)
    //          setTimer("request-channel", RequestChannel(connection), settings.channelReconnectTimeout, true)
    //      }
    case Event(NewChannel(channel), _ %: mode) ⇒
      cancelTimer("request-channel")
      log.debug("Received channel {}", channel)
      channel.addShutdownListener(this)
      registeredCallbacks.foreach(_.apply(channel))
      goto(Available) using ChannelData(Some(channel), mode)
    case Event(WhenAvailable(callback), _) ⇒
      registeredCallbacks += callback
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
    case Event(DeleteExchange(exchange, ifUnused), Some(channel) %: _) ⇒
      channel.exchangeDelete(exchange.name, ifUnused)
      stay()
    case Event(DeleteQueue(queue, ifUnused, ifEmpty), Some(channel) %: _) ⇒
      channel.queueDelete(queue.name, ifUnused, ifEmpty)
      stay()
    case Event(Declare(items @ _*), Some(channel) %: _) ⇒
      items foreach {
        declarable: Declarable[_] ⇒ sender ! declarable.declare(channel)
      }
      stay()
    case Event(ConnectionDisconnected, Some(channel) %: mode) ⇒
      log.warning("Connection went down of channel {}", channel)
      goto(Unavailable) using None %: mode
    case Event(OnlyIfAvailable(callback), Some(channel) %: _) ⇒
      callback.apply(channel)
      stay()
    case Event(WithChannel(callback), Some(channel) %: _) ⇒
      stay() replying callback.apply(channel)

    case Event(WhenAvailable(callback), channel %: _) ⇒
      channel.foreach(callback.apply(_))
      stay()
  }

  whenUnhandled {
    case Event(cause: ShutdownSignalException, _ %: mode) ⇒
      if (cause.isHardError) { // connection error, await ConnectionDisconnected()
        stay()
      } else { // channel error
        val channel = cause.getReference.asInstanceOf[RabbitChannel]
        if (cause.isInitiatedByApplication) {
          log.debug("Channel {} shutdown ({})", channel, cause.getMessage)
          stop()
        } else {
          log.error(cause, "Channel {} broke down", channel)
          context.parent ! RequestNewChannel //tell the connectionActor that a new channel is needed
          goto(Unavailable) using None %: mode
        }
      }
  }

  def shutdownCompleted(cause: ShutdownSignalException) {
    self ! cause
  }

  onTermination {
    case StopEvent(_, _, Some(channel) %: _) ⇒
      if (channel.isOpen) {
        log.debug("Closing channel {}", channel)
        Exception.ignoring(classOf[AlreadyClosedException], classOf[ShutdownSignalException]) {
          channel.close()
        }
      }
  }
}

