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

    "start in state UnavailablePublisher" in {
      channelActor.stateName must be === UnavailablePublisher
    }
        
    "execute registered callbacks and become Available when receiving a Channel" in {
      channelActor.stateName must be === UnavailablePublisher
      val latch = TestLatch(10)
      for (i ← 1 to 5) channelActor ! WhenAvailable(c ⇒ latch.open())
      for (i ← 1 to 5) channelActor ! WithChannel(c ⇒ latch.open())
      channelActor ! NewChannel(channel)
      Await.ready(latch, 5 seconds).isOpen must be === true
      channelActor.stateName must be === AvailablePublisher
    }
    "register callback (WhenAvailable) and execute immediately when Available" in {
      channelActor.stateName must be === AvailablePublisher
      val latch = TestLatch()
      channelActor ! WhenAvailable(c ⇒ latch.open())
      Await.ready(latch, 5 seconds).isOpen must be === true
    }

    "register future (WithChannel) and execute immediately when Available" in {
      channelActor.stateName must be === AvailablePublisher
      val latch = TestLatch()
      channelActor ! WithChannel(c ⇒ latch.open())
      Await.ready(latch, 5 seconds).isOpen must be === true
    }

  

    "request new channel when channel brakes and go to Unavailble" in {
      channelActor ! new ShutdownSignalException(false, false, "Test", channel)
      channelActor.stateName must be === UnavailablePublisher
    }
    "go to Unavailable when connection disconnects" in new TestKit(system) with AmqpMock {
      channelActor.setState(AvailablePublisher, Some(channel))
      channelActor ! ConnectionDisconnected
      channelActor.stateName must be === UnavailablePublisher
    }
    
  }
}
