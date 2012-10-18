//package akka.amqp
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
//import akka.actor.ActorPath
//
//trait MqPublisher {
//  implicit def system : ActorSystem
//  def refFactory : ActorRefFactory = system //override to build the actor from a context
//  
//  implicit val channel = AmqpExtension(system).newChannelForPublisher()
//   
//  val exchange: ExchangeDeclaration
//   val actorSetup : () => Actor
//   
//   val ref = new FutureActor(
//
//	    channel.withChannel{ implicit c =>
//	      
//	   val ref = refFactory.actorOf(Props(actorSetup))
//	   val publisher = channel.newPublisher(exchange(c))
//	   (ref,None) 
//  }
//	  
//	   ) 
//}
//
//trait ReturnToActor { self: MqPublisher =>
//  def returnToActor: ActorPath
//}