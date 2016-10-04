import sbt.Keys._

val ScalaVersion                  = "2.11.8"
val ScalaJsDomVersion             = "0.9.0"
val ScalaJsJQueryVersion          = "0.9.0"
val JUnitInterfaceVersion         = "0.11"

val DefaultOrbeonFormsVersion     = "2016.2-SNAPSHOT"
val DefaultOrbeonEdition          = "CE"

val ExplodedWarWebInf             = "build/orbeon-war/WEB-INF"
val ExplodedWarLibPath            = ExplodedWarWebInf + "/lib"

val ExplodedWarResourcesPath      = ExplodedWarWebInf + "/resources"
val FormBuilderResourcesPathInWar = "forms/orbeon/builder/resources"

val MatchScalaJSFileNameFormatRE  = """((.+)-(fastopt|opt)).js""".r
val MatchRawJarNameRE             = """([^_]+)(?:_.*)?\.jar""".r

val copyJarToExplodedWar          = taskKey[Option[File]]("Copy JAR file to local WEB-INF/bin for development.")
val fastOptJSToExplodedWar        = taskKey[Unit]("Copy fast-optimized JavaScript files to the exploded WAR.")
val fullOptJSToExplodedWar        = taskKey[Unit]("Copy full-optimized JavaScript files to the exploded WAR.")

val orbeonVersionFromProperties   = settingKey[String]("Orbeon Forms version from system properties.")
val orbeonEditionFromProperties   = settingKey[String]("Orbeon Forms edition from system properties.")

// TBH I don't know whether `in ThisBuild` is needed
orbeonVersionFromProperties in ThisBuild := sys.props.get("orbeon.version") getOrElse DefaultOrbeonFormsVersion
orbeonEditionFromProperties in ThisBuild := sys.props.get("orbeon.edition") getOrElse DefaultOrbeonEdition

val PathsToExcludeFromCoreJAR = List(
  "org/orbeon/oxf/servlet/OrbeonXFormsFilter",
  "org/orbeon/oxf/portlet/OrbeonProxyPortlet",
  "org/orbeon/oxf/fr/embedding/servlet/ServletFilter",
  "org/orbeon/oxf/fr/embedding/servlet/API"
)

val JUnitOptions = List(
  //"-q",
  "-v",
  "-s",
  "-a",
  //"--run-listener=org.orbeon.junit.OrbeonJUnitRunListener",
  //"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=61155",
  "-Doxf.resources.factory=org.orbeon.oxf.resources.PriorityResourceManagerFactory",
  "-Doxf.resources.priority.1=org.orbeon.oxf.resources.FilesystemResourceManagerFactory",
  "-Doxf.resources.priority.1.oxf.resources.filesystem.sandbox-directory=src/test/resources",
  "-Doxf.resources.priority.2=org.orbeon.oxf.resources.FilesystemResourceManagerFactory",
  "-Doxf.resources.priority.2.oxf.resources.filesystem.sandbox-directory=src/resources",
  "-Doxf.resources.priority.3=org.orbeon.oxf.resources.FilesystemResourceManagerFactory",
  "-Doxf.resources.priority.3.oxf.resources.filesystem.sandbox-directory=src/resources-packaged",
  "-Doxf.resources.priority.4=org.orbeon.oxf.resources.FilesystemResourceManagerFactory",
  "-Doxf.resources.priority.4.oxf.resources.filesystem.sandbox-directory=src/main/resources",
  "-Doxf.resources.priority.5=org.orbeon.oxf.resources.ClassLoaderResourceManagerFactory",
  "-Doxf.resources.common.min-reload-interval=50",
  "-Doxf.test.config=oxf:/ops/unit-tests/tests.xml",
  "-Djava.io.tmpdir=build/temp/test",
  // Some code uses the default time zone, which might different on different system, so we need to set it explicitly
  "-Duser.timezone=America/Los_Angeles",
  // Getting a JDK error, per http://stackoverflow.com/a/13575810/5295
  "-Djava.util.Arrays.useLegacyMergeSort=true"
)

def deleteAndCreateTempTestDirectory(base: File) = {

  val dir = base / "build" / "temp" / "test"

  IO.delete(dir)
  IO.createDirectory(dir)
}

def copyScalaJSToExplodedWar(sourceFile: File, rootDirectory: File): Unit = {

  val (prefix, optType) =
    sourceFile.name match { case MatchScalaJSFileNameFormatRE(_, prefix, optType) ⇒ prefix → optType }

  val launcherName  = s"$prefix-launcher.js"
  val jsdepsName    = s"$prefix-jsdeps${if (optType == "opt") ".min" else ""}.js"
  val sourceMapName = s"${sourceFile.name}.map"

  val targetDir =
    rootDirectory / ExplodedWarResourcesPath / FormBuilderResourcesPathInWar / "scalajs"

  IO.createDirectory(targetDir)

  val names = List(
    sourceFile                                 → s"$prefix.js",
    (sourceFile.getParentFile / launcherName)  → launcherName,
    (sourceFile.getParentFile / jsdepsName)    → jsdepsName,
    (sourceFile.getParentFile / sourceMapName) → sourceMapName
  )

  for ((sourceFile, newName) ← names) {
    val targetFile = targetDir / newName
    println(s"Copying Scala.js file ${sourceFile.name} to ${targetFile.absolutePath}.")
    IO.copyFile(
      sourceFile           = sourceFile,
      targetFile           = targetFile,
      preserveLastModified = true
    )
  }
}

// Configuration to run database tests only
lazy val DatabaseTest = config("db") extend Test

lazy val commonSettings = Seq(
  organization                  := "org.orbeon",
  version                       := orbeonVersionFromProperties.value,
  scalaVersion                  := ScalaVersion,

  jsEnv                         := JSDOMNodeJSEnv().value,

  javacOptions  ++= Seq(
    "-encoding", "utf8",
    "-source", "1.6",
    "-target", "1.6"
  ),
  scalacOptions ++= Seq(
    "-encoding", "utf8",
    "-feature",
    "-language:postfixOps",
    "-language:reflectiveCalls",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-language:existentials"
    // Consider the following flags
//    "-deprecation",
//    "-unchecked",
//    "-Xfatal-warnings",
//    "-Xlint",
//    "-Yno-adapted-args",
//    "-Ywarn-dead-code",        // N.B. doesn't work well with the ??? hole
//    "-Ywarn-numeric-widen",
//    "-Ywarn-value-discard",
//    "-Xfuture",
//    "-Ywarn-unused-import"     // 2.11 only
  ),

  copyJarToExplodedWar := {

    val sourceJarFile = (packageBin in Compile).value
    val MatchRawJarNameRE(sourceJarRawName) = sourceJarFile.name
    val targetJarFile = new File(ExplodedWarLibPath + '/' + sourceJarRawName + ".jar")

    if (! sourceJarFile.name.contains("_sjs") &&
        sourceJarRawName != "orbeon-form-builder-client" &&
        (! targetJarFile.exists || sourceJarFile.lastModified > targetJarFile.lastModified)) {
      println(s"Copying JAR ${sourceJarFile.name} to ${targetJarFile.absolutePath}.")
      IO.copy(List(sourceJarFile → targetJarFile), overwrite = false, preserveLastModified = false)
      Some(targetJarFile)
    } else {
      None
    }
  }
)

lazy val common = (crossProject.crossType(CrossType.Full) in file("common"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-common"
  )
  .jvmSettings(
    unmanagedBase := baseDirectory.value / ".." / ".." / "lib"
  )
  .jsSettings(
    libraryDependencies += "org.scalactic" %%% "scalactic" % "3.0.0" % "test",
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.0" % "test"
  )

lazy val commonJVM = common.jvm
lazy val commonJS = common.js

lazy val formBuilderShared = (crossProject.crossType(CrossType.Pure) in file("form-builder-shared"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-form-builder-shared"
  )
  .jvmSettings(
  )
  .jsSettings(
  )

lazy val formBuilderSharedJVM = formBuilderShared.jvm.dependsOn(commonJVM)
lazy val formBuilderSharedJS  = formBuilderShared.js.dependsOn(commonJS)

lazy val formBuilderClient = (project in file("form-builder-client"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(formBuilderSharedJS)
  .settings(commonSettings: _*)
  .settings(
    name                           := "orbeon-form-builder-client",

    libraryDependencies            += "org.scala-js" %%% "scalajs-dom"    % ScalaJsDomVersion,
    libraryDependencies            += "be.doeraene"  %%% "scalajs-jquery" % ScalaJsJQueryVersion,

    libraryDependencies            += "org.scalactic" %%% "scalactic" % "3.0.0" % "test",
    libraryDependencies            += "org.scalatest" %%% "scalatest" % "3.0.0" % "test",

    skip in packageJSDependencies  := false,
    jsDependencies                 += "org.webjars" % "jquery" % "1.12.0" / "1.12.0/jquery.js",

    persistLauncher     in Compile := true,
    persistLauncher     in Test    := true,

    testOptions         in Test    += Tests.Setup(() ⇒ println("Setup")),
    testOptions         in Test    += Tests.Cleanup(() ⇒ println("Cleanup")),

    fastOptJSToExplodedWar := copyScalaJSToExplodedWar(
      (fastOptJS in Compile).value.data,
      baseDirectory.value.getParentFile
    ),

    fullOptJSToExplodedWar := copyScalaJSToExplodedWar(
      (fullOptJS in Compile).value.data,
      baseDirectory.value.getParentFile
    )
  )

lazy val xupdate = (project in file("xupdate"))
  .dependsOn(commonJVM)
  .settings(commonSettings: _*)
  .settings(
    name          := "orbeon-xupdate"
  )

lazy val dom = (project in file("dom"))
  .settings(commonSettings: _*)
  .settings(
    name                  := "orbeon-dom",
    unmanagedBase in Test := baseDirectory.value / ".." / "lib"
  )

lazy val formRunner = (project in file("form-runner"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-form-runner"
  )

lazy val formBuilder = (project in file("form-builder"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-form-builder"
  )

lazy val core = (project in file("src"))
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(commonJVM, dom, formBuilderSharedJVM, xupdate, formRunner, formBuilder)
  .configs(DatabaseTest)
  .settings(commonSettings: _*)
  .settings(inConfig(DatabaseTest)(Defaults.testSettings): _*) // and not `Defaults.testTasks`!
  .settings(
    name                               := "orbeon-core",

    buildInfoPackage                   := "org.orbeon.oxf.common",
    buildInfoKeys                      := Seq[BuildInfoKey](
      "orbeonVersion" → orbeonVersionFromProperties.value,
      "orbeonEdition" → orbeonEditionFromProperties.value
    ),

    defaultConfiguration := Some(Compile),

    scalaSource       in Compile       := baseDirectory.value / "main" / "scala",
    javaSource        in Compile       := baseDirectory.value / "main" / "java",
    resourceDirectory in Compile       := baseDirectory.value / "main" / "resources",

    unmanagedBase                      := baseDirectory.value / ".." / "lib",

    // TODO: only src/main/resources/org/orbeon/oxf/xforms/script/coffee-script.js
    // http://www.scala-sbt.org/0.13/docs/Mapping-Files.html
    mappings          in (Compile, packageBin) ~= { _ filterNot { case (_, path) ⇒ PathsToExcludeFromCoreJAR.exists(path.startsWith) } }
  )
  .settings(
    scalaSource       in Test          := baseDirectory.value / "test" / "scala",
    javaSource        in Test          := baseDirectory.value / "test" / "java",
    resourceDirectory in Test          := baseDirectory.value / "test" / "resources",

    libraryDependencies                += "com.novocode" % "junit-interface" % JUnitInterfaceVersion % "test",
    testOptions       in Test          += Tests.Argument(TestFrameworks.JUnit, JUnitOptions: _*),
    testOptions       in Test          += Tests.Filter(s ⇒ s.endsWith("Test") && ! s.contains("CombinedClientTest")),
    testOptions       in Test          += Tests.Setup(() ⇒ deleteAndCreateTempTestDirectory(baseDirectory.value / "..")),
    parallelExecution in Test          := false,
    fork              in Test          := true, // "By default, tests executed in a forked JVM are executed sequentially"
    javaOptions       in Test          ++= Seq("-ea", "-server", "-Djava.awt.headless=true", "-Xms256m", "-Xmx2G", "-XX:MaxPermSize=512m"),
    baseDirectory     in Test          := baseDirectory.value / ".."

    //    libraryDependencies          += "com.lihaoyi" %% "pprint" % "0.4.1",
  )
  .settings(
    scalaSource       in DatabaseTest  := baseDirectory.value / "db" / "scala",
    resourceDirectory in DatabaseTest  := baseDirectory.value / "db" / "resources"
  )

lazy val root = (project in file("."))
  .aggregate(commonJVM, commonJS, dom, formBuilderSharedJVM, xupdate, core, formRunner, formBuilder, formBuilderClient)
  .settings(

    scalaVersion                       := ScalaVersion,

    // TEMP: override so that root project it doesn't search under src
    scalaSource       in Compile       := baseDirectory.value / "root" / "src" / "main" / "scala",
    javaSource        in Compile       := baseDirectory.value / "root" / "src" / "main" / "java",
    resourceDirectory in Compile       := baseDirectory.value / "root" / "src" / "main" / "resources",

    scalaSource       in Test          := baseDirectory.value / "root" / "src" / "test" / "scala",
    javaSource        in Test          := baseDirectory.value / "root" / "src" / "test" / "java",
    resourceDirectory in Test          := baseDirectory.value / "root" / "src" / "test" / "resources",

    publishArtifact                    := false,

    test              in  DatabaseTest := test in core in DatabaseTest
  )

sound.play(compile in Compile, Sounds.Blow, Sounds.Basso)
