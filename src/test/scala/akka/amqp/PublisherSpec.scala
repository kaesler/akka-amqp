//package akka.amqp
//import scala.concurrent.ExecutionContext.Implicits.global
//import akka.actor.ActorSystem
//import akka.testkit.{ AkkaSpec, TestLatch }
//import scala.concurrent.util.duration._
//import scala.concurrent.{ Await, Promise }
//
//class PublisherSpec extends AkkaSpec(AmqpConfig.Valid.config) {
//
//  trait PublisherScope  {
//    def exchange: ExchangeDeclaration
//
//    implicit val ext = AmqpExtension(system)
//    
//    val channel = ext.connection.newChannelForPublisher()
//    
//    val publisher = Await.result(channel.newPublisher(exchange), 50 seconds)
//
//    def after() {
//      channel.stop()
//    }
//  }
//
//  "Durable Publisher" should {
//
//        "publish message on default exchange" in new PublisherScope {
//          def exchange = NamelessExchange
//    
//          try {
//            publisher.publish(Message("test".getBytes, "1.2.3"))
//          } finally { after() }
//        }
//    "kill channel when publishing on non existing exchange" in new PublisherScope {
//   
//      def exchange = Exchange("does-not-exist")("direct")
//
//      try {
//        implicit val system = ActorSystem("amqp")
//        val latch = TestLatch()
//        channel tell { implicit ch ⇒
//          Exchange("does-not-exist").passive(ch).delete(false)
//          ch.addShutdownListener(new ShutdownListener {
//            def shutdownCompleted(cause: ShutdownSignalException) {
//              latch.open()
//            }
//          })
//        }
//        publisher.publish(Message("test".getBytes, "1.2.3"))
//        Await.ready(latch, 5 seconds).isOpen must be === true
//      } finally { after() }
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
//      } finally { after() }
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
//      } finally { after() }
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
