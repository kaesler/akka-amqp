//package akka.amqp
//
//import scala.concurrent.ExecutionContext.Implicits.global
//import akka.actor.ActorSystem
//import akka.testkit.{ AkkaSpec, TestLatch }
//import scala.concurrent.util.duration._
//import scala.concurrent.{ Await, Promise }
//import akka.testkit.AkkaSpec
//import akka.testkit.TestFSMRef
//import ChannelActor._
//import org.mockito.Matchers._
//import org.mockito.Matchers
//import org.mockito.Mockito._
//
//class ConsumerSpec extends AkkaSpec(AmqpConfig.Valid.config) {
//
//  trait ConsumerScope  {
//    implicit val ext = AmqpExtension(system)
//    val channel = ext.connection.newChannelForConsumer()
//    def queue: QueueDeclaration
//    def exchange: ExchangeDeclaration
//    def queueBinding = channel.ask(c => queue(c) << exchange(c) := "ConsumerTest")
//        
//    val consumer = Await.result(channel.newConsumer(queue, testActor, autoAcknowledge = true, queueBinding)), 50 seconds)
//
//    def after() {
//      channel.stop()
//    }
//  }
//
//  "Durable Consumer" should {
//
//        "consume message from default exchange" in new ConsumerScope {
//          def exchange = NamelessExchange
//          def exchange = NamelessExchange
//    
//          try {
//            publisher.publish(Message("test".getBytes, "1.2.3"))
//          } finally { after() }
//        }
//        
//     "consumer message from the amq.fanout exchange" in new ConsumerScope    {
//       
//     }
//    "kill channel when consuming on non existant queue" in new ConsumerScope {
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
//    
//  }
//}
