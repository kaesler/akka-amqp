package akka.amqp

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.ActorSystem
import akka.testkit.{ AkkaSpec, TestLatch }
import scala.concurrent.duration._
import scala.concurrent.{ Await, Promise }
import akka.testkit.AkkaSpec
import akka.testkit.TestFSMRef
import ChannelActor._
import org.mockito.Matchers._
import org.mockito.Matchers
import org.mockito.Mockito._
import akka.testkit.ImplicitSender
import akka.actor.ActorRef
import akka.pattern.ask
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
class ConsumerSpec extends WordSpec with MustMatchers with BeforeAndAfterAll {

  class ConsumerScope extends AkkaSpec(AmqpConfig.Valid.config) with ImplicitSender {
    implicit val timeout = akka.util.Timeout(5 seconds)
    val ext = AmqpExtension(system)
    ext.connectionActor ! Connect

    def runTest[T](test: ⇒ T) = {
      try {
        test
      } finally {
        afterAll
      }
    }

    /**
     * If akkaSpec is used in a scope, it's ActorSystem must be shutdown manually, call this at the end of each scope
     */
    def shutdown = afterAll

  }

  "Durable Consumer" should {

    "consume message from nameless exchange on DefaultQueue" in new ConsumerScope {
      runTest {

        val consumerActor = Await.result((ext.connectionActor ? CreateChannel(false)).mapTo[ActorRef], 5 seconds)
        val qBinding = Exchange.nameless >> Queue.default.active()
        consumerActor ! Consumer(testActor, autoAck = true, Seq(qBinding))
        val defaultQueue = expectMsgType[DeclaredQueue]
        val publisherActor = Await.result((ext.connectionActor ? CreateChannel(false)).mapTo[ActorRef], 5 seconds)

        publisherActor ! Publisher(Some(testActor))

        publisherActor ! PublishToExchange(Message("test".getBytes, defaultQueue.name), NamelessExchange.name)
        expectMsgType[Delivery]
      }
    }

    "consume message from nameless exchange on namedQueue" in new ConsumerScope {
      runTest {
        val consumerActor = Await.result((ext.connectionActor ? CreateChannel(false)).mapTo[ActorRef], 5 seconds)
        val queueName = "namedQueue"
        val qBinding = Exchange.nameless >> Queue.named(queueName).active(false, true, true, None)
        consumerActor ! Consumer(testActor, autoAck = true, Seq(qBinding))
        Thread.sleep(1000) //CAUTION: if the consumerActor has not finished declaring the Consumer then it will not receive the message, so we wait. I do not expect this to occur in production use, if it does then a confirming publisher should be used.
        val publisherActor = Await.result((ext.connectionActor ? CreateChannel(false)).mapTo[ActorRef], 5 seconds)

        publisherActor ! Publisher(Some(testActor))

        publisherActor ! PublishToExchange(Message("test".getBytes, queueName), NamelessExchange.name)
        expectMsgType[Delivery]
      }
    }

    "send message to named exchange and consume from namedQueue" in new ConsumerScope {
      runTest {
        val consumerActor = Await.result((ext.connectionActor ? CreateChannel(false)).mapTo[ActorRef], 5 seconds)
        val queueName = "namedQueue"
        val exchangeName = "namedExchange"
        val routingKey = "routeMe"
        val qBinding = Exchange.named(exchangeName).active("direct", false, true) >> Queue.named(queueName).active(false, true, true, None) := routingKey
        consumerActor ! Consumer(testActor, autoAck = true, Seq(qBinding))
        Thread.sleep(1000) //CAUTION: if the consumerActor has not finished declaring the Consumer then it will not receive the message, so we wait. I do not expect this to occur in production use, if it does then a confirming publisher should be used.
        val publisherActor = Await.result((ext.connectionActor ? CreateChannel(false)).mapTo[ActorRef], 5 seconds)

        publisherActor ! Publisher(Some(testActor))

        publisherActor ! PublishToExchange(Message("test".getBytes, routingKey), exchangeName)
        expectMsgType[Delivery]
      }
    }

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

  }
}
