package akka.amqp

import java.io.IOException

import akka.actor.ActorRef
import akka.event.LoggingAdapter

import ChannelActor._
import Queue._

//trait CanStop {
//  def stop : Unit
//}
//
case class ReturnedMessage(replyCode: Int,
                           replyText: String,
                           exchange: String,
                           routingKey: String,
                           properties: BasicProperties,
                           body: Array[Byte])

case class Delivery(payload: Array[Byte],
                    routingKey: String,
                    deliveryTag: Long,
                    isRedeliver: Boolean,
                    properties: BasicProperties,
                    channelActor: ActorRef,
                    log: LoggingAdapter) {

  def acknowledge(deliveryTag: Long, multiple: Boolean = false): Boolean = {
    if (!channelActor.isTerminated) {
      // TODO: Should we use WithChannel here instead of OnlyIfAvailable?.
      // I.e. are we risking failing to ack?
      channelActor ! OnlyIfAvailable { channel ⇒
        channel.basicAck(deliveryTag, multiple)
        log.debug("Acked tag {} for routingKey {}", deliveryTag, routingKey)
      }
      true
    } else {
      log.debug("Ack of deliveryTag {} for routingKey {} failed omitted because channel actor terminated",
        deliveryTag, routingKey)
      false
    }
  }

  def reject(deliveryTag: Long, reQueue: Boolean = false) {
    if (!channelActor.isTerminated) channelActor ! OnlyIfAvailable { channel ⇒
      channel.basicReject(deliveryTag, reQueue)
      log.debug("Rejected deliveryTag {}, reqQueue = {}", deliveryTag, reQueue)
    }
    else
      log.debug("Rejection omitted for deliveryTag {} because channel actor terminated", deliveryTag)
  }
}

//object DurableConsumer {
//  import scala.concurrent.ExecutionContext.Implicits.global
//
//  def apply(channel: RabbitChannel)(queue: DeclaredQueue,
//                      deliveryHandler: ActorRef,
//                      autoAck: Boolean,
//                      queueBindings: QueueBinding*) : DurableConsumer = {
//    implicit val c = channel
//   new DurableConsumer(queue,deliveryHandler,autoAck, queueBindings : _*)
//  }
//
//  def apply(queue: DeclaredQueue,
//                      deliveryHandler: ActorRef,
//                      autoAck: Boolean,
//                      queueBindings: QueueBinding*) : Future[DurableConsumer] = {
//
//   durableChannel.withChannel{ implicit c =>
//      new DurableConsumer(queue,deliveryHandler,autoAck, queueBindings : _*)
//
//      }
//  }
//
//}
case object StopConsuming
trait ChannelConsumer { channelActor: ChannelActor ⇒

  /**
   * declare QueueBindings if given, declare Queue and Exchange if they were given to the QueueBinding as undeclared
   * setup the Consumer and store the tag.
   * track the Queue and Exchange if they were declared, as well as the tag and QueueBinding.
   *
   */
  def setupConsumer(channel: RabbitChannel, listener: ActorRef, autoAck: Boolean, bindings: Seq[QueueBinding]): ConsumerMode = {

    //use mutable values to track what queues and exchanges have already been declared
    var uniqueDeclaredQueues = List.empty[DeclaredQueue]
    var uniqueDeclaredExchanges = List.empty[DeclaredExchange]
    var defaultQueue: Option[DeclaredQueue] = None
    bindings foreach { binding ⇒

      val declaredExchange = binding.exchange match {
        case e: UndeclaredExchange ⇒ e.declare(channel, context.system)
        case e: DeclaredExchange   ⇒ e
      }

      //add to declaredQueues on declare, so that declaredQueues is unique
      val declaredQueue = binding.queue match {
        case q: UndeclaredQueue if (q.nameOption.isEmpty) ⇒
          if (defaultQueue.isEmpty) {
            //if the Default Queue is declared, return to sender.
            val declared = q.declare(channel, context.system)
            defaultQueue = Some(declared)
            sender ! declared
            declared
          } else {
            defaultQueue.get
          }
        case q: UndeclaredQueue ⇒
          val dqOption = uniqueDeclaredQueues.collectFirst { case dq if dq.name == q.nameOption.get ⇒ dq }
          if (dqOption.isEmpty) {
            val declared = q.declare(channel, context.system)
            uniqueDeclaredQueues = declared :: uniqueDeclaredQueues
            declared
          } else {
            dqOption.get
          }
        case q: DeclaredQueue ⇒
          if (!uniqueDeclaredQueues.contains(q)) {
            uniqueDeclaredQueues = q :: uniqueDeclaredQueues
          }
          q
      }

      if (declaredExchange.name != "") { //do not QueueBind the namelessExchange
        //declare queueBinding
        import scala.collection.JavaConverters._
        //require(binding.routingKey != null, "the routingKey must not be null to bind " + declaredExchange.name + " >> " + declaredQueue.name)
        val ok = channel.queueBind(declaredQueue.name, declaredExchange.name, binding.routingKey, binding.getArgs.map(_.toMap.asJava).getOrElse(null))
        context.system.eventStream.publish(NewlyDeclared(DeclaredQueueBinding(Some(ok), declaredQueue, declaredExchange, binding)))
      }
    }

    val distinctlyNamedQueues = defaultQueue.toList ::: uniqueDeclaredQueues

    val tags = distinctlyNamedQueues map { queue ⇒
      val tag = channel.basicConsume(queue.name, autoAck, new DefaultConsumer(channel) {
        override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) {
          import envelope._
          log.debug("handleDelivery() called from Java layer: queue is {}, consumer is {}, deliveryTag is {}",
            queue.name, getConsumerTag, getDeliveryTag.toString)
          listener ! Delivery(body, getRoutingKey, getDeliveryTag, isRedeliver, properties, context.self, log)
        }

        @throws(classOf[IOException])
        override def handleCancel(consumerTag: String) =
          log.debug("Consumer cancellation notification received for {}", consumerTag)
      })

      log.debug("Registered with the Java client library a consumer with tag {}", tag)
      tag
    }
    ConsumerMode(listener, autoAck, bindings, tags)
  }

  when(Available) {
    case Event(Consumer(listener, autoAck, bindings), Some(channel) %: _ %: _) ⇒
      log.debug("Switching to Consumer Mode, Available")
      stay() using stateData.toMode(setupConsumer(channel, listener, autoAck, bindings))
    case Event(StopConsuming, ChannelData(Some(channel), _, cm: ConsumerMode)) ⇒
      stay() using stateData.toMode(cm cancelTags channel);
  }

  def consumerUnhandled: StateFunction = {
    case Event(Consumer(listener, autoAck, bindings), data) ⇒
      log.debug("Switching to Consumer Mode, Not Available")
      //switch modes and save the message contents so that when we transition back to Available we know how to wire things up
      stay() using stateData.toMode(ConsumerMode(listener, autoAck, bindings, Seq.empty))

  }

  onTransition {
    //really would prefer to just "Let it crash" when a channel disconnects,
    //instead of reloading the state for this actor...not sure how that would work with the autoreconnect functionality though
    case Unavailable -> Available if nextStateData.isConsumer ⇒
      val _ %: _ %: ConsumerMode(listener, autoAck, bindings, _) = nextStateData
      context.self ! Consumer(listener, autoAck, bindings) //switch to modes again before unstashing messages!
      unstashAll()
  }

  def consumerTermination: PartialFunction[StopEvent, Unit] = {
    case StopEvent(_, state, ChannelData(Some(channel), callbacks, cm: ConsumerMode)) ⇒
      log.debug("\n\n\nConsumer terminating\n\n\n")
      cm.cancelTags(channel)
      terminateWhenActive(channel)
  }
}
