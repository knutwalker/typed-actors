import sbt._
import sbt.Keys._
import de.knutwalker.sbt._
import de.knutwalker.sbt.KSbtKeys._

object Build extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = KSbtPlugin

  override lazy val projectSettings = List(
                organization := "de.knutwalker",
                   startYear := Some(2015),
                  maintainer := "Paul Horn",
               githubProject := Github("knutwalker", "typed-actors"),
                 description := "Compile time wrapper for more type safe actors",
                scalaVersion := "2.11.7",
          crossScalaVersions := scalaVersion.value :: "2.10.5" :: Nil,
         libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.12" % "provided",
                 javaVersion := JavaVersion.Java17,
             autoAPIMappings := true,
                    pomExtra := pomExtra.value ++
                      <properties>
                        <info.apiURL>http://{githubProject.value.org}.github.io/{githubProject.value.repo}/api/{version.value}/</info.apiURL>
                      </properties>
  )
}
