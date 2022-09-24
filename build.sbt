import Dependencies._

val scala213 = "2.13.9"
val scala3   = "3.2.0"

ThisBuild / organization := "io.soujiro32167"
ThisBuild / scalaVersion := scala3
ThisBuild / description  := "A ZIP compression lib for ZIO"
ThisBuild / licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / developers := List(
  Developer("soujiro32167", "Eli Kasik", "soujiro32167@gmail.com", url("https://github.com/soujiro32167"))
)

ThisBuild / resolvers += "jitpack" at "https://jitpack.io"

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val `zio1-zip-core` = (project in file("zio1-zip-core"))
  .settings(
    name := "zio1-zip",
    libraryDependencies ++= Seq(
      `dev.zio`.zio1.zio,
      `dev.zio`.zio1.test,
      `dev.zio`.zio1.`test-sbt`,
      `dev.zio`.zio1.`zio-streams`
    ),
    testFrameworks     := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    crossScalaVersions := Seq(scala213, scala3),
    scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => List("-Xsource:3")
        case _            => Nil
      }
    }
  )

lazy val `zio-zip-core` = (project in file("zio-zip-core"))
  .settings(
    name := "zio-zip",
    libraryDependencies ++= Seq(
      `dev.zio`.zio.zio,
      `dev.zio`.zio.test,
      `dev.zio`.zio.`test-sbt`,
      `dev.zio`.zio.`zio-streams`
    ),
    testFrameworks     := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    crossScalaVersions := Seq(scala3),
    scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => List("-Xsource:3")
        case _            => Nil
      }
    }
  )

lazy val `zio-zip` = (project in file("."))
  .aggregate(`zio1-zip-core`, `zio-zip-core`)
  .settings(publish / skip := true, crossScalaVersions := Nil)
