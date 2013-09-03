import sbt._
import sbt.Keys._

object PluginBuild extends Build {

  lazy val playTS = Project(
    id = "play-ts", base = file(".")
  ).settings(
    sbtPlugin := true,
    name := "play-ts",
    description := "SBT plugin for handling TypeScript assets in Play 2",
    organization := "com.arpnetworking",
    version := "0.2-SNAPSHOT",
    scalaVersion := "2.9.2",
    sbtVersion := "0.12.2",

    resolvers += "Local Maven" at Path.userHome.asFile.toURI.toURL + ".m2/repository",
    libraryDependencies ++= Seq(
      "com.mangofactory" % "typescript4j" % "0.4.0-SNAPSHOT"
    )
  )
}
