import com.typesafe.sbt.SbtGit.GitKeys
import com.typesafe.sbt.git.NullLogger
import sbt._
import sbt.Keys._
import de.knutwalker.sbt._
import de.knutwalker.sbt.KSbtKeys._
import sbtrelease.Version

object Build extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = KSbtPlugin

  val akkaVersion = "2.3.12"

  object autoImport {
    lazy val genModules = taskKey[Seq[(File, String)]]("generate module files for guide")
  }
  import autoImport.genModules

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
           genModules := generateModules(state.value, sourceManaged.value, streams.value.cacheDirectory, thisProject.value.dependencies),
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

  def generateModules(state: State, dir: File, cacheDir: File, modules: Seq[ClasspathDep[ProjectRef]]): Seq[(File, String)] = {
    val files = new GenerateModulesTask(state, dir, cacheDir, modules.map(_.project)).apply()
    files.map(x ⇒ (x, x.getName))
  }

  private class GenerateModulesTask(state: State, dir: File, cacheDir: File, modules: Seq[ProjectRef]) {
    val tempModulesFile = cacheDir / "gen-modules" / "modules.yml"
    val tempVersionFile = cacheDir / "gen-modules" / "version.yml"
    val modulesFile = dir / "modules.yml"
    val versionFile = dir / "version.yml"

    def apply(): Seq[File] = {
      mkFiles()
      List(
        cachedCopyOf(tempVersionFile, versionFile),
        cachedCopyOf(tempModulesFile, modulesFile)
      )
    }

    def mkFiles() = {
      val extracted = Project.extract(state)
      val latestVersion = getLatestVersion(extracted)
      val lines = mkLines(extracted, latestVersion)
      IO.writeLines(tempModulesFile, lines)
      IO.writeLines(tempVersionFile, s"version: $latestVersion" :: Nil)
    }

    def cachedCopyOf(from: File, to: File): File = {
      val cacheFile = cacheDir / "gen-modules" / "cached-inputs" / from.getName
      val check = Tracked.inputChanged(cacheFile) {(hasChanged, input: HashFileInfo) ⇒
        if (hasChanged || !to.exists()) {
          IO.copyFile(from, to, preserveLastModified = true)
        }
      }
      check(FileInfo.hash(from))
      to
    }

    def getLatestVersion(extracted: Extracted): String = {
      val baseDir = extracted.get(baseDirectory)
      val currentVersion = extracted.get(version)
      val (_, runner) = extracted.runTask(GitKeys.gitRunner, state)
      val tagDashEl = runner("tag", "-l")(baseDir, NullLogger)
      val tags = tagDashEl.trim.split("\\s+").toSeq.map(_.replaceFirst("^v", ""))
      val sortedTags = tags.flatMap(Version(_)).sorted.map(_.string)
      sortedTags.lastOption.getOrElse(currentVersion)
    }

    def mkLines(extracted: Extracted, latestVersion: String) =
      modules.flatMap { proj ⇒
        Seq(
          s"- organization: ${extracted.get(organization in proj)}",
          s"  name: ${extracted.get(name in proj)}",
          s"  version: $latestVersion"
        )
      }
  }

  implicit val versionOrdering = new Ordering[Version] {
    def compare(x: Version, y: Version): Int =
      x.major compare y.major match {
        case 0 ⇒ x.minor.getOrElse(0) compare y.minor.getOrElse(0) match {
          case 0 ⇒ x.bugfix.getOrElse(0) compare y.bugfix.getOrElse(0) match {
            case 0 ⇒ (x.qualifier, y.qualifier) match {
              case (None, None) ⇒ 0
              case (Some(_), Some(_)) ⇒ 0
              case (None, _) ⇒ 1
              case (_, None) ⇒ -1
            }
            case a ⇒ a
          }
          case a ⇒ a
        }
        case a ⇒ a
      }
  }
}
