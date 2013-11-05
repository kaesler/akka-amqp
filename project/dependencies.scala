import sbt._

object dependencies {
  def Scalatest    = "org.scalatest" %% "scalatest" % "2.0.M5b" % "test"   	  // ApacheV2
  def scalaActorsForScalaTest = "org.scala-lang" % "scala-actors" % "2.10.3" % "test"
  def AmqpClient = "com.rabbitmq" % "amqp-client" % "2.8.7"   													  // ApacheV2


  def AkkaAgent = "com.typesafe.akka" %% "akka-agent" % "2.2.3"

  def Specs2      = "org.specs2"                 % "specs2_2.10"              % "1.13"        % "test"  // MIT
  def JUnit = "junit" % "junit" % "4.7" % "test"   																 // Common Public License 1.0
  def AkkaTestKit = "com.typesafe.akka" %% "akka-testkit" % "2.2.3" % "test"
  def Mockito = "org.mockito" % "mockito-all" % "1.9.0" % "test"     											// MIT
}
