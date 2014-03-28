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
    version := "0.7-SNAPSHOT",
    scalaVersion := "2.10.3",
    sbtVersion := "0.13.0",
    addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.2"),
    resolvers += "Local Maven" at Path.userHome.asFile.toURI.toURL + ".m2/repository",
    libraryDependencies ++= Seq(
      "com.mangofactory" % "typescript4j" % "0.5.0-SNAPSHOT"
    ),

  credentials += Credentials.apply("Sonatype Nexus Repository Manager", "nexus-dev", "deployment", "deployment"),
    pomIncludeRepository := { _ => false },
    publishMavenStyle := true,
//    publishTo <<= version {
//      (v: String) =>
//        val nexus = "https://oss.sonatype.org/"
//        if (v.trim.endsWith("SNAPSHOT"))
//          Some("snapshots" at nexus + "content/repositories/snapshots")
//        else
//          Some("releases" at nexus + "service/local/staging/deploy/maven2")
//    },
      publishTo <<= version {
        (v: String) =>
          val nexus = "http://nexus-dev/content/repositories/"
          if (v.trim.endsWith("SNAPSHOT"))
            Some("snapshots" at nexus + "snapshots/")
          else
            Some("releases" at nexus + "releases/")
      },

    licenses := Seq("Apache-2" -> url("http://opensource.org/licenses/Apache-2.0")),
    homepage := Some (url("https://github.com/BrandonArp/play-ts")),
    pomExtra := (
        <scm>
          <url>git@github.com:BrandonArp/play-ts.git</url>
          <connection>scm:git:git@github.com:BrandonArp/play-ts.git</connection>
        </scm>
        <developers>
          <developer>
            <id>barp</id>
            <name>Brandon Arp</name>
            <email>brandonarp@gmail.com</email>
          </developer>
        </developers>)
  )
}
