package akka.amqp

import akka.actor.FSM.Transition
import akka.actor.ActorSystem
import akka.testkit.{ AkkaSpec, TestLatch, TestKit, TestFSMRef }
import scala.concurrent.util.duration._
import scala.concurrent.Await
import org.mockito.Matchers._
import org.mockito.Matchers
import org.mockito.Mockito._
import ChannelActor._
class ChannelSpec extends AkkaSpec(AmqpConfig.Valid.config) with AmqpMock {

  "Durable Channel Actor" should {
    //  implicit val system = ActorSystem("channelspec")
    val channelActor = TestFSMRef(new ChannelActor(AmqpConfig.Valid.settings))

    "start in state Unavailable" in {
      channelActor.stateName must be === Unavailable
    }
    "request a channel when connection becomes Connected" in {
      within(5 seconds) {
        channelActor ! Transition(testActor, Disconnected, Connected)
        expectMsgType[WithConnection[Unit]]
      }
    }
    "execute registered callbacks and become Available when receiving a Channel" in {
      channelActor.stateName must be === Unavailable
      val latch = TestLatch(10)
      for (i ← 1 to 5) channelActor ! WhenAvailable(c ⇒ latch.open())
      for (i ← 1 to 5) channelActor ! WithChannel(c ⇒ latch.open())
      channelActor ! channel
      Await.ready(latch, 5 seconds).isOpen must be === true
      channelActor.stateName must be === Available
    }
    "register callback (WhenAvailable) and execute immediately when Available" in {
      channelActor.stateName must be === Available
      val latch = TestLatch()
      channelActor ! WhenAvailable(c ⇒ latch.open())
      Await.ready(latch, 5 seconds).isOpen must be === true
    }

    "register future (WithChannel) and execute immediately when Available" in {
      channelActor.stateName must be === Available
      val latch = TestLatch()
      channelActor ! WithChannel(c ⇒ latch.open())
      Await.ready(latch, 5 seconds).isOpen must be === true
    }

    "register PublishToExchange and execute immediately when Available" in {
      channelActor.stateName must be === Available

      val exchangeName = "someExchange"
      val routingKey = ""
      val mess = "test"
      val mandatory = false
      val immediate = false

      val ans = answer(channel)(c ⇒ c.basicPublish(Matchers.eq(exchangeName), Matchers.eq(routingKey), eqBool(mandatory), eqBool(immediate), any(), any()))

      channelActor ! PublishToExchange(Message(mess, routingKey), exchangeName, false)

      awaitCond(ans.once, 5 seconds, 250 milli)
      // verify(channel).basicPublish(Matchers.eq(exchangeName), Matchers.eq(routingKey), eqBool(mandatory), eqBool(immediate), any(), any())
    }

    "request new channel when channel brakes and go to Unavailble" in {
      channelActor ! new ShutdownSignalException(false, false, "Test", channel)
      channelActor.stateName must be === Unavailable
    }
    "go to Unavailable when connection disconnects" in new TestKit(system) with AmqpMock {
      channelActor.setState(Available, Some(channel))
      channelActor ! Transition(testActor, Connected, Disconnected)
      channelActor.stateName must be === Unavailable
    }
    "if channel disconnect ands reconnects we receive a new channel object in the actor" in pending
  }
}
