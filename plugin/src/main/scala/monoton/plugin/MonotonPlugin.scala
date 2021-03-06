package guttural.plugin

import sbt._
import sbt.Keys._

object MonotonPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def projectSettings = super.projectSettings ++ Seq(
    mainClass in Compile := Some("guttural.server.Main"),
    libraryDependencies += "dev.aoiroaoino" %% "guttural" % "0.1.0-SNAPSHOT"
  )
}
