import sbt._
import sbt.Keys._
import de.knutwalker.sbt._
import de.knutwalker.sbt.KSbtKeys._

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
    val file = new GenerateModulesTask(state, dir, cacheDir, modules.map(_.project)).apply()
    Seq(file → file.getName)
  }

  private class GenerateModulesTask( state: State, dir: File, cacheDir: File, modules: Seq[ProjectRef]) {

    val tempFile = cacheDir / "gen-modules" / "modules.yml"
    val targetFile = dir / "modules.yml"

    def apply(): File = {
      mkTemp()
      cachedCopyFile(FileInfo.hash(tempFile))
      targetFile
    }

    def mkTemp() = {
      val lines = mkLines
      IO.writeLines(tempFile, lines)
    }

    val cachedCopyFile =
      Tracked.inputChanged(cacheDir / "gen-modules" / "cached-inputs") { (inChanged, input: HashFileInfo) =>
        if (inChanged || !targetFile.exists) {
          IO.copyFile(tempFile, targetFile, preserveLastModified = true)
        }
      }

    def mkLines = {
      val extracted = Project.extract(state)
      modules.flatMap { proj ⇒
        Seq(
          s"- organization: ${extracted.get(organization in proj)}",
          s"  name: ${extracted.get(name in proj)}",
          s"  version: ${extracted.get(version in proj)}"
        )
      }
    }
  }
}
