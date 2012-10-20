//package akka.amqp
//import scala.concurrent.ExecutionContext.Implicits.global
//import akka.actor.ActorSystem
//import akka.testkit.{ AkkaSpec, TestLatch }
//import scala.concurrent.util.duration._
//import scala.concurrent.{ Await, Promise }
//import akka.testkit.TestFSMRef
//import ChannelActor._
//import org.mockito.Matchers._
//import org.mockito.Matchers
//import org.mockito.Mockito._
//import akka.pattern.ask
//import akka.actor._
//class PublisherSpec extends AkkaSpec(AmqpConfig.Valid.config) with AmqpMock {
//
//  implicit val timeout = akka.util.Timeout(5 seconds)
//  
//  trait FakeScope {        
//    val channelActor = TestFSMRef(new ChannelActor(AmqpConfig.Valid.settings))
//    channelActor ! NewChannel(channel)
//  }
//  
//  trait PublisherScope  {
//    def exchange: DeclarableExchange
//
//    val ext = AmqpExtension(system)
//    
//   val channelActor = Await.result((ext.connectionActor ? CreateChannel()).mapTo[ActorRef],5 seconds) 
//    
//    
//      channelActor ! NewChannel(channel)
//      
//      
//
//   def after {
//      channelActor ! PoisonPill
//    }
//  }
//
//  "Durable Publisher" should {
//
//        "register PublishToExchange and execute immediately when Available" in new FakeScope {
//      channelActor.stateName must be === AvailablePublisher
//
//      val exchangeName = "someExchange"
//      val routingKey = ""
//      val mess = "test"
//      val mandatory = false
//      val immediate = false
//
//      val ans = answer(channel)(c ⇒ c.basicPublish(Matchers.eq(exchangeName), Matchers.eq(routingKey), eqBool(mandatory), eqBool(immediate), any(), any()))
//
//      channelActor ! PublishToExchange(Message(mess, routingKey), exchangeName, false)
//
//      awaitCond(ans.once, 5 seconds, 250 milli)
//    }
//    "kill channel when publishing on non existing exchange" in new PublisherScope {
//   
//      def exchange = Exchange("does-not-exist")("direct")
//
//      try {
//        implicit val system = ActorSystem("amqp")
//        val latch = TestLatch()
//        //channel tell { implicit ch ⇒
//      //  val exch = Await.result((channelActor ? Declare(Exchange("does-not-exist").passive)).mapTo[NamedExchange], 5 seconds)
//        
//        
//        for (exch <- (channelActor ? Declare(Exchange("does-not-exist").passive)).mapTo[NamedExchange]) {
//            channelActor ! DeleteExchange(exch, ifUnused = false)
//        }
//         channelActor ! WithChannel(_.addShutdownListener(new ShutdownListener {
//            def shutdownCompleted(cause: ShutdownSignalException) {
//              latch.open()
//            }
//          }))
//        //}
//          channelActor ! PublishToExchange(Message("test".getBytes, "1.2.3"), exchange.name, false)
//        Await.ready(latch, 5 seconds).isOpen must be === true
//      } finally { after }
//    }
//    "get message returned when sending with immediate flag" in new PublisherScope {
//      def exchange = Exchange.nameless
//
//      try {
//        val latch = TestLatch()
//        publisher.onReturn { returnedMessage ⇒
//          latch.open()
//        }
//        publisher.publish(Message("test".getBytes, "1.2.3", immediate = true))
//        Await.ready(latch, 5 seconds).isOpen must be === true
//      } finally { after }
//      
//    }
//    "get message returned when sending with mandatory flag" in new PublisherScope {
//      def exchange = Exchange.nameless
//      try {
//        val latch = TestLatch()
//        publisher.onReturn { returnedMessage ⇒
//          latch.open()
//        }
//        publisher.publish(Message("test".getBytes, "1.2.3", mandatory = true))
//        Await.ready(latch, 50 seconds).isOpen must be === true
//      } finally { after }
//    }
//    "get message publishing acknowledged when using confirming publiser" in  {
//
//    
//      val system = ActorSystem("amqp")
//      implicit val ext = AmqpExtension(system)
//      val durableConnection = ext.connection
//      
//      val channel = durableConnection.newChannelForConfirmingPublisher
//         
//      val confirmingPublisher = channel.newConfirmingPublisher( NamelessExchange)
//      
//      try {
//        val future = confirmingPublisher.publishConfirmed(Message("test".getBytes, "1.2.3"))
//        val promise = Promise.successful(Ack).future
//        Await.ready(future, 5 seconds).value must be === Await.ready(promise,5 seconds).value
//      } finally {
//        channel.stop()
//      }
//    }
//  }
//}
