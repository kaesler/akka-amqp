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

  case class ChannelData private[amqp] (channel: Option[RabbitChannel], callbacks: Vector[RabbitChannel ⇒ Unit], mode: ChannelMode) {
    def toUnavailable = ChannelData(None, callbacks, mode)
    def toAvailable(channel: RabbitChannel) = ChannelData(Some(channel), callbacks, mode)
    def addCallback(callback: RabbitChannel ⇒ Unit) = ChannelData(channel, callbacks :+ callback, mode)
  }

  object %: {
    def unapply(channelData: ChannelData): Option[(Option[RabbitChannel], ChannelData0)] = {
      import channelData._
      Some((channel, ChannelData0(callbacks, mode)))
    }
    def unapply(channelData: ChannelData0): Option[(Vector[RabbitChannel ⇒ Unit], ChannelMode)] = {
      import channelData._
      Some((callbacks, mode))
    }
  }

  private[amqp] case class ChannelData0(callbacks: Vector[RabbitChannel ⇒ Unit], mode: ChannelMode) {
    def %:(channel: Option[RabbitChannel]): ChannelData = ChannelData(channel, callbacks, mode)
  }

  sealed trait ChannelMode {
    def %:(callbacks: Vector[RabbitChannel ⇒ Unit]): ChannelData0 = ChannelData0(callbacks, this)
  }
  /**
   * A basicChannel may declare/delete Queues and Exchanges.
   * It may also publish but will not send back Returned or Confirm messages
   */
  case object BasicChannel extends ChannelMode

  /**
   * The given listening ActorRef receives messages that are Returned or Confirmed
   */
  case class ConfirmingPublisher(listener: ActorRef) extends ChannelMode

  /**
   * The given listening ActorRef receives messages that are Returned
   */
  case class Publisher(listener: Option[ActorRef] = None) extends ChannelMode

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
   * Code to execute whenever a new channel is received
   */
  case class ExecuteOnNewChannel(callback: RabbitChannel ⇒ Unit)

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
//with ChannelPublisher
private[amqp] abstract class ChannelActor(settings: AmqpSettings)
  extends Actor with FSM[ChannelState, ChannelData] with ShutdownListener {
  //perhaps registered callbacks should be replaced with the akka.actor.Stash
  //val registeredCallbacks = new collection.Seq[RabbitChannel ⇒ Unit]
  val serialization = SerializationExtension(context.system)

  def stash(): Unit
  def unstashAll(): Unit

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

  startWith(Unavailable, ChannelData(None, Vector.empty, BasicChannel))

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
    case Event(NewChannel(channel), _ %: callbacks %: mode) ⇒
      cancelTimer("request-channel")
      log.debug("Received channel {}", channel)
      channel.addShutdownListener(this)
      callbacks.foreach(_.apply(channel))
      goto(Available) using stateData.toAvailable(channel)
    case Event(WithChannel(callback), _) ⇒
      stash()
      stay()
    case Event(OnlyIfAvailable(callback), _) ⇒
      stay()

    case Event(_: DeleteExchange, _) | Event(_: DeleteQueue, _) | Event(_: Declare, _) ⇒
      stash()
      stay()
  }

  when(Available) {
    case Event(DeleteExchange(exchange, ifUnused), Some(channel) %: _ %: _) ⇒
      channel.exchangeDelete(exchange.name, ifUnused)
      stay()
    case Event(DeleteQueue(queue, ifUnused, ifEmpty), Some(channel) %: _ %: _) ⇒
      channel.queueDelete(queue.name, ifUnused, ifEmpty)
      stay()
    case Event(Declare(items @ _*), Some(channel) %: _ %: _) ⇒
      items foreach {
        declarable: Declarable[_] ⇒ sender ! declarable.declare(channel)
      }
      stay()
    case Event(ConnectionDisconnected, Some(channel) %: _ %: _) ⇒
      log.warning("Connection went down of channel {}", channel)
      goto(Unavailable) using stateData.toUnavailable
    case Event(OnlyIfAvailable(callback), Some(channel) %: _ %: _) ⇒
      callback.apply(channel)
      stay()
    case Event(WithChannel(callback), Some(channel) %: _ %: _) ⇒
      stay() replying callback.apply(channel)
  }

  whenUnhandled {
    case Event(ExecuteOnNewChannel(callback), _) ⇒
      stay() using stateData.addCallback(callback)
    case Event(cause: ShutdownSignalException, _ %: callbacks %: mode) ⇒
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
          goto(Unavailable) using stateData.toUnavailable
        }
      }
  }

  onTransition {
    case Unavailable -> Available ⇒ unstashAll()
  }

  def shutdownCompleted(cause: ShutdownSignalException) {
    self ! cause
  }

  onTermination {
    case StopEvent(_, _, Some(channel) %: _ %: _) ⇒
      if (channel.isOpen) {
        log.debug("Closing channel {}", channel)
        Exception.ignoring(classOf[AlreadyClosedException], classOf[ShutdownSignalException]) {
          channel.close()
        }
      }
  }
}

