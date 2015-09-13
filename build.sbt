import sbt._, Keys._
import com.typesafe.sbt.SbtSite.SiteKeys._
import com.typesafe.sbt.SbtGhPages.GhPagesKeys._
import sbtunidoc.Plugin.UnidocKeys.{ unidoc, unidocProjectFilter }

lazy val core = project settings (name := "typed-actors")

lazy val creator = project dependsOn core settings (
  name := "typed-actors-creator",
  libraryDependencies += "com.chuusai" %% "shapeless" % "2.2.5")

lazy val tests = project dependsOn (core, creator) settings (
  dontRelease,
  libraryDependencies ++= List(
    "org.specs2" %% "specs2-core"          % "3.6.4" % "test",
    "org.specs2" %% "specs2-matcher-extra" % "3.6.4" % "test"))

lazy val examples = project dependsOn (core, creator) settings dontRelease

lazy val docs = project dependsOn (core, creator) settings (
  dontRelease,
  unidocSettings,
  site.settings,
  ghpages.settings,
  tutSettings,
  unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(core, creator),
  site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), "api"),
  site.addMappingsToSiteDir(tut, "tut"),
  ghpagesNoJekyll := false,
  scalacOptions in (ScalaUnidoc, unidoc) ++= Seq(
    "-doc-source-url", scmInfo.value.get.browseUrl + "/tree/master€{FILE_PATH}.scala",
    "-sourcepath", baseDirectory.in(LocalRootProject).value.getAbsolutePath,
    "-doc-title", githubProject.value.repo,
    "-doc-version", version.value,
    "-diagrams",
    "-groups"
  ),
  git.remoteRepo := s"git@github.com:${githubProject.value.org}/${githubProject.value.repo}.git",
  includeFilter in makeSite ~= (_ || "*.yml" || "*.md" || "*.scss"),
  tutScalacOptions ~= (_.filterNot(Set("-Xfatal-warnings", "-Ywarn-unused-import", "-Ywarn-dead-code"))),
  watchSources <++= (tutSourceDirectory, siteSourceDirectory, includeFilter in makeSite) map { (t, s, f) ⇒ (t ** "*.md").get ++ (s ** f).get }
)

lazy val parent = project in file(".") dependsOn creator aggregate (core, creator, tests, examples) settings dontRelease

addCommandAlias("travis", ";clean;coverage;test;coverageReport;coverageAggregate")
