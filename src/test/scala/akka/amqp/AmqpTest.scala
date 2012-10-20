package akka.amqp

import org.mockito.stubbing.Answer
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.mockito.invocation.InvocationOnMock
import akka.testkit.TestKit
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.Future
import reflect.ClassTag
import akka.testkit.TestFSMRef
import akka.testkit.AkkaSpec
import akka.agent.Agent

trait AmqpTest {
  import akka.actor._
  import akka.pattern.ask
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val to = akka.util.Timeout(5 seconds)
  implicit def system: ActorSystem
  def connectionActor: ActorRef
  def rabbitConnectionAwait = Await.result((connectionActor ? WithConnection(x ⇒ x)).mapTo[RabbitConnection], 5 seconds)
  def withConnectionAwait[T: ClassTag](callback: RabbitConnection ⇒ T)(duration: Duration) = Await.result(withConnection(callback), duration)

  def withConnection[T: ClassTag](callback: RabbitConnection ⇒ T): Future[T] = {

    (connectionActor ? WithConnection(callback)).mapTo[T]
  }

}

trait AmqpMock extends MockitoSugar {
  val channel = mock[RabbitChannel]
  val connection = mock[RabbitConnection]

  def eqBool(x: Boolean): java.lang.Boolean = org.mockito.Matchers.eq(x: java.lang.Boolean)

  when(channel.getConnection).thenReturn(connection)

  /**
   * allows one to more easily find out how many times a method was executed
   */
  def answer[T, Y](x: T)(xMethod: T ⇒ Y) = setupAnswer(x)(xMethod)(_ ⇒ ())
  def setupAnswer[T, Y](x: T)(xMethod: T ⇒ Y)(runnableAnswer: InvocationOnMock ⇒ Unit = _ ⇒ ()): MyAnswer = {
    val answer = new MyAnswer(runnableAnswer)

    xMethod(doAnswer(answer).when(x))

    answer
  }

}

class MyAnswer(invoke: InvocationOnMock ⇒ Unit) extends Answer[Unit] {
  val lock: AnyRef = new Object
  @volatile
  private var runCount: Int = 0
  def count = lock.synchronized {
    runCount
  }
  def atLeastOnce = lock.synchronized {
    runCount > 0
  }
  def once = lock.synchronized {
    runCount == 1
  }

  override def answer(invocation: InvocationOnMock): Unit = lock.synchronized {
    runCount = runCount + 1
    invoke(invocation)
  }
}
