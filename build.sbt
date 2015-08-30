lazy val core = project settings (name := "typed-actors")

lazy val creator = project dependsOn core settings (
  name := "typed-actors-creator",
  libraryDependencies += "com.chuusai" %% "shapeless" % "2.2.5")

lazy val tests = project dependsOn (core, creator) settings (
  dontRelease,
  libraryDependencies ++= List(
    "org.specs2" %% "specs2-core"          % "3.6.4" % "test",
    "org.specs2" %% "specs2-matcher-extra" % "3.6.4" % "test"))

lazy val parent = project in file(".") dependsOn creator aggregate (core, creator, tests) settings dontRelease

addCommandAlias("travis", ";clean;coverage;test;coverageReport;coverageAggregate")
