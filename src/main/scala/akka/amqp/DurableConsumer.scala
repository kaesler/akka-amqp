package akka.amqp
//import Queue._
//
//import java.util.concurrent.atomic.AtomicReference
//import java.util.concurrent.{ CountDownLatch, TimeUnit }
//import util.control.Exception
//
//import java.io.IOException
//import akka.actor.ActorRef
//import akka.event.Logging
//import scala.concurrent.Future
//import scala.concurrent.Promise
//import akka.actor.ActorSystem
//
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

//case class Delivery(payload: Array[Byte],
//                    routingKey: String,
//                    deliveryTag: Long,
//                    isRedeliver: Boolean,
//                    properties: BasicProperties,
//                    sender: CanBuildDurableConsumer#DurableConsumer) {
//
//  def acknowledge() {
//    sender.acknowledge(deliveryTag)
//  }
//
//  def reject() {
//    sender.reject(deliveryTag)
//  }
//}
//
//case class GeneratedQueueDeclared(queueName: String)
//
//trait CanBuildDurableConsumer { durableChannel : DurableConnection#DurableChannel =>
//import durableChannel._
//import durableChannel.extension._
//
// def newConsumer(queue: DeclaredQueue, deliveryHandler: ActorRef, autoAcknowledge: Boolean, queueBindings: QueueBinding*): Future[DurableConsumer] = {
//   DurableConsumer(queue, deliveryHandler, autoAcknowledge, queueBindings: _*)//(this, system)   
// }
//
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
//
//
//class DurableConsumer private[DurableConsumer](
//					  queue: DeclaredQueue,
//                      deliveryHandler: ActorRef,
//                      autoAcknowledge: Boolean,
//                      queueBindings: QueueBinding*) extends CanStop {
//  outer ⇒
//  
//val channelActor = durableChannel.channelActor
//  private val log = Logging(system, this.getClass)
//
//  val consumerTag = new AtomicReference[Option[String]](None)
//  //val latch = new CountDownLatch(1)
//
//  durableChannel.whenAvailable {implicit channel =>
//      queueBindings.foreach {   
//        _.bind
//      }
//      val tag = channel.basicConsume(queue.name, autoAcknowledge, new DefaultConsumer(channel) {
//        override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) {
//          import envelope._
//          deliveryHandler ! Delivery(body, getRoutingKey, getDeliveryTag, isRedeliver, properties, outer)
//        }
//      })
//      consumerTag.set(Some(tag))
//}
//  def acknowledge(deliveryTag: Long, multiple: Boolean = false) {
//    if (!channelActor.isTerminated) channelActor ! OnlyIfAvailable(_.basicAck(deliveryTag, multiple))
//  }
//
//  def reject(deliveryTag: Long, reQueue: Boolean = false) {
//    if (!channelActor.isTerminated) channelActor ! OnlyIfAvailable(_.basicReject(deliveryTag, reQueue))
//  }
//def stop = stopConsumeAndChannel()
//  def stopConsumeAndChannel() {
//    if (!channelActor.isTerminated) {
//      for (tag ← consumerTag.get()) {
//        channelActor ! OnlyIfAvailable { channel ⇒
//          Exception.ignoring(classOf[ShutdownSignalException], classOf[IOException]) {
//            channel.basicCancel(tag)
//          }
//        }
//      }
//    }
//    durableChannel.stop()
//  }
//}
//}