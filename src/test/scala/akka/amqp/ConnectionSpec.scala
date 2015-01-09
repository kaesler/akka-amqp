package akka.amqp
import akka.agent.Agent
import akka.actor.FSM.{ UnsubscribeTransitionCallBack, CurrentState, Transition, SubscribeTransitionCallBack }
import akka.testkit.{ AkkaSpec, TestKit, TestFSMRef }
import akka.actor.{ ActorSystem, PoisonPill }
import scala.concurrent.duration._
import scala.concurrent.{ Await }
import com.typesafe.config.ConfigFactory
import scala.concurrent.Promise
import akka.pattern.ask
import org.scalatest.{ WordSpec, BeforeAndAfterAll, Tag, BeforeAndAfter }
import org.scalatest.matchers.MustMatchers
import com.rabbitmq.client.Method
class ValidConnectionSpec extends WordSpec with MustMatchers with BeforeAndAfterAll {

  abstract class AkkaScope extends AkkaSpec(AmqpConfig.Valid.config) with AmqpTest {
    import system.dispatcher
    val connectionStatusAgent = Agent(false)

    val connectionActor = TestFSMRef(new ConnectionActor(AmqpConfig.Valid.settings, connectionStatusAgent))
    def isConnected = connectionStatusAgent()

    /**
     * If akkaSpec is used in a scope, it's ActorSystem must be shutdown manually, call this at the end of each scope
     */
    def shutdown = afterAll

  }

  "Durable Connection Actor" should {

    "start disconnected" in new AkkaScope {
      connectionActor.stateName must be === Disconnected
      isConnected must be === false
      shutdown
    }
    "connect" in new AkkaScope {
      connectionActor.stateName must be === Disconnected
      isConnected must be === false
      connectionActor ! Connect
      connectionActor.stateName must be === Connected
      isConnected must be === true

      shutdown
    }

    "reconnect on ShutdownSignalException" in new AkkaScope with AmqpMock {

      try {
        within(5 second) {
          connectionActor ! Connect
          connectionActor ! SubscribeTransitionCallBack(testActor)
          expectMsg(CurrentState(connectionActor, Connected))
          connectionActor ! new ShutdownSignalException(true, false, null, connection)
          expectMsg(Transition(connectionActor, Connected, Disconnected))
          expectMsg(Transition(connectionActor, Disconnected, Connected)) //reconnect success
        }
      } finally {
        connectionActor ! UnsubscribeTransitionCallBack(testActor)
        testActor ! PoisonPill
        shutdown
      }
    }
    "disconnect" in new AkkaScope {
      connectionActor ! Connect
      isConnected must be === true
      connectionActor.stateName must be === Connected
      connectionActor ! Disconnect
      connectionActor.stateName must be === Disconnected
      isConnected must be === false
      shutdown
    }
    "dispose (PoisonPill)" in new AkkaScope {
      connectionActor ! Connect
      val conn = rabbitConnectionAwait
      conn.isOpen() must be === true
      isConnected must be === true
      connectionActor.stateName must be === Connected
      connectionActor ! PoisonPill
      connectionActor.isTerminated must be === true
      isConnected must be === false
      conn.isOpen() must be === false //make sure that the connection is really closed.
      shutdown
    }
    "close the Rabbit Connection when the ActorSystem shuts down" in new AkkaScope {
      connectionActor ! Connect
      val conn: RabbitConnection = rabbitConnectionAwait
      conn.isOpen() must be === true
      system.shutdown

      awaitCond(conn.isOpen() == false, 5 seconds, 100 milli)
      shutdown
    }
    "propagate state transition to child Actors" in new AkkaScope {
      import ChannelActor.{ Available, Unavailable }

      val channel = Await.result((connectionActor ? CreateChannel(false)).mapTo[akka.actor.ActorRef], 1 second)
      channel ! SubscribeTransitionCallBack(testActor)
      expectMsg(CurrentState(channel, Unavailable))

      connectionActor ! Connect
      expectMsg(Transition(channel, Unavailable, Available))

      connectionActor ! Disconnect
      expectMsg(Transition(channel, Available, Unavailable))

      // reconnect
      connectionActor ! Connect
      expectMsg(Transition(channel, Unavailable, Available))
    }
    "send new channels to child Actors on reconnect" in pending
    "send disconnect message to child Actors on disconnect" in pending
    "send NewChannel message to child actor upon creation in Connected state" in pending
    "do not send NewChannel message to child actor upon creation in Disconnected state" in pending

    "Durable Connection" should {
      "execute callback on connection when connected" in new AkkaScope {
        connectionActor ! Connect
        try {
          connectionActor ! SubscribeTransitionCallBack(testActor)
          expectMsg(CurrentState(connectionActor, Connected))
          val portFuture = withConnection(_.getPort)
          val promise = Promise.successful(5672).future
          Await.ready(portFuture, 5 seconds).value must be === Await.ready(promise, 5 seconds).value
        } finally {
          shutdown
        }
      }
    }
  }
}

class NoConnectionSpec extends AkkaSpec(AmqpConfig.Invalid.config) {
  "Durable Connection" should {
    "never connect using non existing host addresses" in {
      import system.dispatcher
      val connectionStatusAgent = akka.agent.Agent(false)
      val connectionActor = TestFSMRef(new ConnectionActor(AmqpConfig.Invalid.settings, connectionStatusAgent))
      try {
        connectionActor ! Connect
        within(2500 milli) {
          connectionActor ! SubscribeTransitionCallBack(testActor)
          expectMsg(CurrentState(connectionActor, Disconnected))
        }
        connectionActor.stateName must be === Disconnected
      } finally {
        testActor ! PoisonPill
        connectionActor ! Disconnect // to cancel reconnect timer
        connectionActor ! PoisonPill
        afterAll //shutdown the ActorSystem
      }
    }
  }
}

