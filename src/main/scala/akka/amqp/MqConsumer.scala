package akka.amqp
//
//import akka.actor.Actor
//import akka.actor.ActorSystem
//import java.io.IOException
//import akka.actor.ActorRefFactory
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.Future
//import akka.actor.ActorRef
//import akka.actor.Props
//import akka.actor.PoisonPill
//
//
//trait Initializer {
//  def Queue : option
//  def Exchange ?: option
//  def Queubinding : List
//} 

//case object QueueBound
//case class QueueBindFailure(exception:IOException)
//
//class FutureActor(future : Future[(ActorRef,Option[CanStop])]) {
//  
//  def !(msg: Any) : Unit = { future.foreach( x => x._1 ! msg ) } 
//  
//  import akka.pattern.ask
//  def ?(msg: Any)(implicit timeout: akka.util.Timeout) = future flatMap { case (ref,_) =>
//    ref ? msg
//  }
// 
//  def stop =  { future.foreach{ x => x._1 ! PoisonPill; x._2 foreach (_.stop) } }
//  
//  def actorPath = { future map { case (ref, _) => ref.path    }}
//  
//}
//
//trait MqConsumer {
//  implicit def system : ActorSystem
//  def refFactory : ActorRefFactory = system //override to build the actor from a context
//  
//  implicit val channel = AmqpExtension(system).newChannelForConsumer(false)
//
//   def queue: QueueDeclaration
//   def autoAck : Boolean
//   def queueBindings : Seq[QueueBinding]
//   val actorSetup : () => Actor
//   
//   val ref = new FutureActor(
//	    channel.withChannel{ implicit c => 
//	   val declaredQueue = queue(c)  
//	   val ref = refFactory.actorOf(Props(actorSetup))
//	   val consumer = channel.DurableConsumer(c)(declaredQueue, ref, autoAck, queueBindings : _*)
//	   (ref,Some(consumer)) 
//  }
//	  
//	   )
// 
//}