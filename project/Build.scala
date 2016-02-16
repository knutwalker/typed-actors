import bintray.BintrayKeys.{ bintrayPackage , bintray ⇒ bt }
import de.knutwalker.sbt._
import de.knutwalker.sbt.KSbtKeys._
import de.knutwalker.sbt.KReleaseSteps._
import com.typesafe.sbt.SbtGit.git
import sbt.Keys._
import sbt._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations.{inquireVersions => _, setReleaseVersion => _, _}


object Build extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = KSbtPlugin

  object autoImport {
    lazy val akkaActorVersion = settingKey[String]("Version of akka-actor.")
    lazy val isAkka24 = settingKey[Boolean]("Whether the build is compiled against Akka 2.4.x.")
    lazy val akkaPersistence = akkaPersistenceDependency
    lazy val akkaActor = akkaDependency
  }
  import autoImport._

  override lazy val projectSettings = Seq(
          projectName := "typed-actors",
         organization := "de.knutwalker",
            startYear := Some(2015),
           maintainer := "Paul Horn",
        githubProject := Github("knutwalker", projectName.value),
       bintrayPackage := projectName.value,
          description := "Compile time wrapper for more type safe actors",
         scalaVersion := "2.11.7",
     akkaActorVersion := "2.3.14",
             isAkka24 := akkaActorVersion.value.startsWith("2.4"),
          javaVersion := JavaVersion.Java17,
         apiMappings ++= mapAkkaJar((externalDependencyClasspath in Compile).value, scalaBinaryVersion.value, akkaActorVersion.value),
            publishTo := { if (!publishArtifact.value) None else if (git.gitCurrentTags.value.isEmpty) (publishTo in bt).value else publishTo.value },
       releaseProcess := getReleaseSteps(isAkka24.value),
    unmanagedSourceDirectories in Compile ++= List(
      if (isAkka24.value) (sourceDirectory in Compile).value / s"scala-akka-2.4.x"
      else                (sourceDirectory in Compile).value / s"scala-akka-2.3.x",
      if (isAkka24.value) (sourceDirectory in (Test, test)).value / s"scala-akka-2.4.x"
      else                (sourceDirectory in (Test, test)).value / s"scala-akka-2.3.x"
    )
  )

  val akkaPersistenceDependency = (_: String) match {
    case x if x.startsWith("2.4") ⇒ "com.typesafe.akka" %% "akka-persistence" % x % "provided"
    case otherwise                ⇒ "com.typesafe.akka" %% "akka-persistence-experimental" % otherwise % "provided"
  }
  val akkaDependency = (_: String) match {
    case x if x.startsWith("2.4") ⇒ "com.typesafe.akka" %% "akka-actor" % x % "provided"
    case otherwise                ⇒ "com.typesafe.akka" %% "akka-actor" % otherwise % "provided"
  }

  def getReleaseSteps(isAkka24: Boolean) = {
    val always = List(
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishSignedArtifacts,
      releaseToCentral
    )
    val versionSpecific =
      if (isAkka24) Nil else List(
        pushGithubPages,
        commitTheReadme,
        setNextVersion,
        commitNextVersion
      )
    val after = List(pushChanges)
    always ++ versionSpecific ++ after
  }

  def mapAkkaJar(cp: Seq[Attributed[File]], crossVersion: String, akkaVersion: String): Map[File, URL] =
    cp.collect {
      case file if file.data.toPath.endsWith(s"akka-actor_$crossVersion-$akkaVersion.jar") ⇒
        (file.data, url(s"http://doc.akka.io/api/akka/$akkaVersion/"))
    }.toMap
}
