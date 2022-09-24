import sbt._

object Dependencies {

  case object `dev.zio` {

    abstract class SomeZio(version: String) {
      val zio           = "dev.zio" %% "zio"          % version
      val test          = "dev.zio" %% "zio-test"     % version % "test"
      val `test-sbt`    = "dev.zio" %% "zio-test-sbt" % version % "test"
      val `zio-streams` = "dev.zio" %% "zio-streams"  % version
    }
    object zio1 extends SomeZio("1.0.16")

    object zio extends SomeZio("2.0.2")
  }
}
