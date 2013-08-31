name := "play-ts"

organization := "com.arpnetworking"

version := "0.1-SNAPSHOT"

libraryDependencies ++= Seq(
	"com.mangofactory" % "typescript4j" % "0.4.0-SNAPSHOT"
)

scalaVersion := "2.9.2"

sbtVersion := "0.12.2"

resolvers += "Local Maven" at Path.userHome.asFile.toURI.toURL + ".m2/repository"

sbtPlugin := true

