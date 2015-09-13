import sbt._
import sbt.Keys._
import de.knutwalker.sbt._
import de.knutwalker.sbt.KSbtKeys._

object Build extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = KSbtPlugin

  val akkaVersion = "2.3.12"

  override lazy val projectSettings = List(
         organization := "de.knutwalker",
            startYear := Some(2015),
           maintainer := "Paul Horn",
        githubProject := Github("knutwalker", "typed-actors"),
          description := "Compile time wrapper for more type safe actors",
         scalaVersion := "2.11.7",
  libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion % "provided",
          javaVersion := JavaVersion.Java17,
      autoAPIMappings := true,
         apiMappings ++= mapAkkaJar((externalDependencyClasspath in Compile).value, scalaBinaryVersion.value),
             pomExtra := pomExtra.value ++
               <properties>
                 <info.apiURL>http://{githubProject.value.org}.github.io/{githubProject.value.repo}/api/{version.value}/</info.apiURL>
               </properties>
  )

  def mapAkkaJar(cp: Seq[Attributed[File]], crossVersion: String): Map[File, URL] =
    cp.collect {
      case file if file.data.toPath.endsWith(s"akka-actor_$crossVersion-$akkaVersion.jar") ⇒
        (file.data, url(s"http://doc.akka.io/api/akka/$akkaVersion/"))
    }.toMap
}
