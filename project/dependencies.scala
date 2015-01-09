import sbt._

object dependencies {
  def Scalatest    = "org.scalatest" %% "scalatest" % "2.2.3" % "test"
  def scalaActorsForScalaTest = "org.scala-lang" % "scala-actors" % "2.11.4" % "test"
  def AmqpClient = "com.rabbitmq" % "amqp-client" % "3.4.2"
  def AkkaAgent = "com.typesafe.akka" %% "akka-agent" % "2.3.8"
  def Specs2      = "org.specs2"                 % "specs2_2.11"              % "2.3.12"        % "test"
  def JUnit = "junit" % "junit" % "4.7" % "test"
  def AkkaTestKit = "com.typesafe.akka" %% "akka-testkit" % "2.3.8" % "test" 
  def Mockito = "org.mockito" % "mockito-all" % "1.9.0" % "test"
}
