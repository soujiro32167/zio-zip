import Dependencies._

val scala213 = "2.13.8"
val scala3   = "3.1.3"

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

lazy val `zio-zip` = (project in file("."))
  .aggregate(`zio-zip-core`, `zio-zip-docs`)
  .settings(
    // crossScalaVersions must be set to Nil on the aggregating project
    // https://www.scala-sbt.org/1.x/docs/Cross-Build.html#Cross+building+a+project
    crossScalaVersions := Nil,
    publish / skip     := true
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
    crossScalaVersions := Seq(scala213, scala3)
  )

lazy val `zio-zip-docs` = project
  .in(file("zio-zip-docs"))
  .settings(
    publish / skip := true,
    moduleName     := "zio-zip-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(`dev.zio`.zio.zio),
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(`zio-zip-core`),
    ScalaUnidoc / unidoc / target := (LocalRootProject / baseDirectory).value / "website" / "static" / "api",
    cleanFiles += (ScalaUnidoc / unidoc / target).value,
    docusaurusCreateSite := docusaurusCreateSite
      .dependsOn(Compile / unidoc)
      .value,
    docusaurusPublishGhpages := docusaurusPublishGhpages
      .dependsOn(Compile / unidoc)
      .value
  )
  .dependsOn(`zio-zip-core`)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
