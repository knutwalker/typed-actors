import Build.autoImport._ // screw you, IntelliJ

lazy val core = project settings (name := "typed-actors")

lazy val creator = project dependsOn core settings (
  libraryDependencies += "com.chuusai" %% "shapeless" % "2.2.5")

lazy val tests = project dependsOn (core, creator) settings (
  dontRelease,
  libraryDependencies ++= List(
    "org.specs2" %% "specs2-core"          % "3.6.5" % "test",
    "org.specs2" %% "specs2-matcher-extra" % "3.6.5" % "test"))

lazy val examples = project dependsOn (core, creator, tests % "test->test") settings (
  dontRelease,
  libraryDependencies += akkaPersistence(akkaActorVersion.value)
)

lazy val docs = project dependsOn (core, creator) settings (
  tutsSettings(core, creator),
  libraryDependencies += akkaPersistence(akkaActorVersion.value))

lazy val parent = project in file(".") dependsOn (core, creator) aggregate (core, creator, tests, examples) settings parentSettings()

addCommandAlias("travis", ";clean;coverage;testOnly -- timefactor 3;coverageReport;coverageAggregate;docs/makeSite")
