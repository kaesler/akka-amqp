import sbt._

object dependencies {
  def Scalatest    = "org.scalatest" %% "scalatest" % "2.2.0" % "test"   	  // ApacheV2
  def scalaActorsForScalaTest = "org.scala-lang" % "scala-actors" % "2.11.1" % "test"
  def AmqpClient = "com.rabbitmq" % "amqp-client" % "2.8.7"   													  // ApacheV2


  def AkkaAgent = "com.typesafe.akka" %% "akka-agent" % "2.3.3"

  def Specs2      = "org.specs2"                 % "specs2_2.11"              % "2.3.12"        % "test"  // MIT
  def JUnit = "junit" % "junit" % "4.7" % "test"   																 // Common Public License 1.0
  def AkkaTestKit = "com.typesafe.akka" %% "akka-testkit" % "2.3.3" % "test"
  def Mockito = "org.mockito" % "mockito-all" % "1.9.0" % "test"     											// MIT
}
