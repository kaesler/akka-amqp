package akka.amqp
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.ActorSystem
import akka.testkit.{ AkkaSpec, TestLatch }
import scala.concurrent.duration._
import scala.concurrent.{ Await, Promise }
import akka.testkit.TestFSMRef
import ChannelActor._
import org.mockito.Matchers._
import org.mockito.Matchers
import org.mockito.Mockito._
import akka.pattern.ask
import akka.actor._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
class PublisherSpec extends WordSpec with MustMatchers with BeforeAndAfterAll {

  class PublisherScope extends AkkaSpec(AmqpConfig.Valid.config) with ImplicitSender {
    implicit val timeout = akka.util.Timeout(5 seconds)
    val ext = AmqpExtension(system)
    ext.connectionActor ! Connect
    val channelActor = Await.result((ext.connectionActor ? CreateChannel()).mapTo[ActorRef], 5 seconds)

    /**
     * If akkaSpec is used in a scope, it's ActorSystem must be shutdown manually, call this at the end of each scope
     */
    def shutdown = afterAll

  }

  class FakeScope extends TestKit(ActorSystem("fake")) with AmqpMock {

    val channelActor = TestFSMRef(new ChannelActor(AmqpConfig.Valid.settings) {
      def stash(): Unit = {}
      def unstashAll(): Unit = {}
    })
    channelActor ! NewChannel(channel)
  }

  "Durable Publisher" should {

    "register PublishToExchange and execute immediately when Available" in new FakeScope {
      channelActor.stateName must be === Available

      val exchangeName = "someExchange"
      val routingKey = ""
      val mess = "test"
      val mandatory = false
      val immediate = false

      val ans = answer(channel)(c ⇒ c.basicPublish(Matchers.eq(exchangeName), Matchers.eq(routingKey), eqBool(mandatory), eqBool(immediate), any(), any()))

      channelActor ! PublishToExchange(Message(mess, routingKey), exchangeName, false)

      awaitCond(ans.once, 5 seconds, 250 milli)
      system.shutdown
    }
    "kill channel when publishing on non existing exchange" in new PublisherScope {
      val exchange = Exchange("does-not-exist")

      try {
        channelActor ! Declare(exchange.active("direct"))
        expectMsgType[NamedExchange]
        for (exch ← (channelActor ? Declare(exchange.passive)).mapTo[NamedExchange]) {
          channelActor ! DeleteExchange(exch, ifUnused = false)
        }
        channelActor ! OnlyIfAvailable {
          _.addShutdownListener(new ShutdownListener {

            def shutdownCompleted(cause: ShutdownSignalException) {
              testActor ! cause
            }
          })
        }
        channelActor ! PublishToExchange(Message("test".getBytes, "1.2.3"), exchange.name, false)
        expectMsgType[ShutdownSignalException]
      } finally { shutdown }
    }
    "get message returned when sending with immediate flag" in new PublisherScope {
      def exchange = Exchange.nameless

      try {
        //  val latch = TestLatch()
        channelActor ! Publisher(Some(testActor))
        //        publisher.onReturn { returnedMessage ⇒
        //          latch.open()
        //        }
        channelActor ! PublishToExchange(Message("test".getBytes, "1.2.3Immediate", immediate = true), exchange.name)
        //publisher.publish(Message("test".getBytes, "1.2.3", immediate = true))
        expectMsgPF[ReturnedMessage](5 seconds) {
          case m @ ReturnedMessage(313, "NO_CONSUMERS", _, "1.2.3Immediate", _, _) ⇒ m
        }
      } finally { shutdown }

    }
    "get message returned when sending with mandatory flag" in new PublisherScope {
      def exchange = Exchange.nameless
      try {
        //val latch = TestLatch()
        channelActor ! Publisher(Some(testActor))
        //publisher.onReturn { returnedMessage ⇒
        //  latch.open()
        //}
        //publisher.publish(Message("test".getBytes, "1.2.3", mandatory = true))
        channelActor ! PublishToExchange(Message("test".getBytes, "1.2.3Mandatory", mandatory = true), exchange.name)
        //Await.ready(latch, 50 seconds).isOpen must be === true
        expectMsgPF[ReturnedMessage](5 seconds) {
          case m @ ReturnedMessage(312, "NO_ROUTE", _, "1.2.3Mandatory", _, _) ⇒ m
        }

      } finally { shutdown }
    }
    "get message publishing acknowledged when using confirming publiser" in new PublisherScope {
      def exchange = Exchange.nameless

      //val system = ActorSystem("amqp")
      //implicit val ext = AmqpExtension(system)

      //val channelActor = Await.result((ext.connectionActor ? CreateChannel()).mapTo[ActorRef], 5 seconds)
      channelActor ! ConfirmingPublisher(testActor)

      //val durableConnection = ext.connection

      // val channel = durableConnection.newChannelForConfirmingPublisher

      //    val confirmingPublisher = channel.newConfirmingPublisher( NamelessExchange)

      try {
        channelActor ! PublishToExchange(Message("test".getBytes, "1.2.3"), exchange.name, confirm = true)
        //val future = confirmingPublisher.publishConfirmed(Message("test".getBytes, "1.2.3"))

        expectMsg(5 seconds, Ack)
        //Await.result(future, 5 seconds) must be === Ack
      } finally {
        system.shutdown
      }
    }
  }
}
