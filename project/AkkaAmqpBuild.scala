import sbt._
import Keys._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

object AkkaAmqpBuild extends Build {
  import dependencies._


    lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test    := formattingPreferences
  )

  def formattingPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
    .setPreference(RewriteArrowSymbols, true)
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)
  }

  lazy val standardSettings = Project.defaultSettings ++ formatSettings ++ Seq(
  	resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
    resolvers += "Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/releases/",
    organization := "com.github.cessationoftime",
    version			 := "2.2-SNAPSHOT",
    scalaVersion := "2.11.4"
  )

  //  lazy val amqp = Project(
  //  id = "akka-amqp",
  //  base = file("akka-amqp"),
  //  dependencies = Seq(actor, actorTests % "test->test", testkit % "test->test"),
  //  settings = defaultSettings ++ Seq(
  //    libraryDependencies ++= Dependencies.amqp
  //  )
 // )

  lazy val root = Project(
      id        = "akka-amqp",
      base      = file("."),
      settings = standardSettings ++ Seq(
    	  libraryDependencies ++= Seq(
	      AmqpClient,
	      AkkaAgent,
	      Specs2,
              JUnit,
	      Scalatest,
	      scalaActorsForScalaTest,
	      //ActorTests,
              AkkaTestKit,
              Mockito)
        )
    )
}
