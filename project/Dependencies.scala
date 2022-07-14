import sbt._

object Dependencies {

  case object `dev.zio` {
    case object zio {
      val zio           = "dev.zio" %% "zio"          % "1.0.15"
      val test          = "dev.zio" %% "zio-test"     % "1.0.15" % "test"
      val `test-sbt`    = "dev.zio" %% "zio-test-sbt" % "1.0.15" % "test"
      val `zio-streams` = "dev.zio" %% "zio-streams"  % "1.0.15"
    }
  }
}
