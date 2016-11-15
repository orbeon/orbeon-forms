import sbt.Keys._

val ScalaVersion                  = "2.11.8"
val ScalaJsDomVersion             = "0.9.0"
val ScalaJsJQueryVersion          = "0.9.0"
val JUnitInterfaceVersion         = "0.11"
val UPickleVersion                = "0.4.1"
val Parboiled1Version             = "1.1.7"
val SprayJsonVersion              = "1.3.2"
val JodaTimeVersion               = "2.1"
val JodaConvertVersion            = "1.2"
val ServletApiVersion             = "3.0.1"
val PortletApiVersion             = "2.0"

val DefaultOrbeonFormsVersion     = "2016.2-SNAPSHOT"
val DefaultOrbeonEdition          = "CE"

val ExplodedWarWebInf             = "build/orbeon-war/WEB-INF"
val ExplodedWarLibPath            = ExplodedWarWebInf + "/lib"
val LiferayWarLibPath             = "/Users/ebruchez/OF/liferay-portal-6.2-ce-ga6/tomcat-7.0.62/webapps/proxy-portlet/WEB-INF/lib"

val ExplodedWarResourcesPath      = ExplodedWarWebInf + "/resources"
val FormBuilderResourcesPathInWar = "forms/orbeon/builder/resources"

val MatchScalaJSFileNameFormatRE  = """((.+)-(fastopt|opt)).js""".r
val MatchJarNameRE                = """(.+)\.jar""".r
val MatchRawJarNameRE             = """([^_]+)(?:_.*)?\.jar""".r

val copyJarToExplodedWar           = taskKey[Option[File]]("Copy JAR file to local WEB-INF/lib for development.")
val copyDependenciesToExplodedWar  = taskKey[Unit]("Copy managed library JAR files to WEB-INF/lib.")
val fastOptJSToExplodedWar         = taskKey[Unit]("Copy fast-optimized JavaScript files to the exploded WAR.")
val fullOptJSToExplodedWar         = taskKey[Unit]("Copy full-optimized JavaScript files to the exploded WAR.")
val copyJarToLiferayWar            = taskKey[Option[File]]("Copy JAR file to Liferay WEB-INF/lib for development.")

val orbeonVersionFromProperties    = settingKey[String]("Orbeon Forms version from system properties.")
val orbeonEditionFromProperties    = settingKey[String]("Orbeon Forms edition from system properties.")

// TBH I don't know whether `in ThisBuild` is needed
// "ThisBuild is a Scope encompassing all projects"
orbeonVersionFromProperties in ThisBuild := sys.props.get("orbeon.version") getOrElse DefaultOrbeonFormsVersion
orbeonEditionFromProperties in ThisBuild := sys.props.get("orbeon.edition") getOrElse DefaultOrbeonEdition

val PathsToExcludeFromCoreJAR = Nil

val JarFilesToExcludeFromWar = Set(
  "orbeon-form-builder-client",
  "orbeon-form-builder-shared",
  "orbeon-proxy-portlet"
)

val JarFilesToExcludeFromLiferayWar = Set(
  "orbeon-form-builder-client",
  "orbeon-form-builder-shared"
)

val TestResourceManagerPaths = List(
  "src/test/resources",    // so that Java processor tests work
  "src/resources",         // ultimately should be moved
  "src/resources-packaged" // ultimately should be moved
)

val ResourceManagerProperties: List[String] = {

  val pkg = "org.orbeon.oxf.resources"

  val props =
    for ((dir, i) ← TestResourceManagerPaths.zipWithIndex)
      yield
        s"-Doxf.resources.priority.${i + 1}=$pkg.FilesystemResourceManagerFactory"           ::
        s"-Doxf.resources.priority.${i + 1}.oxf.resources.filesystem.sandbox-directory=$dir" ::
        Nil

  s"-Doxf.resources.factory=$pkg.PriorityResourceManagerFactory"                             ::
    props.flatten                                                                            :::
    s"-Doxf.resources.priority.${props.size + 1}=$pkg.ClassLoaderResourceManagerFactory"     ::
    Nil
}

val TestJavaOptions = List(
  "-ea",
  "-server",
  "-Djava.awt.headless=true",
  "-Xms256m",
  "-Xmx2G",
  // Unneeded with Java 8
  "-XX:MaxPermSize=512m",
  // Some code uses the default time zone, which might different on different system, so we need to set it explicitly
  "-Duser.timezone=America/Los_Angeles",
  "-Doxf.resources.common.min-reload-interval=50",
//  "-Djava.io.tmpdir=build/temp/test",
  // Getting a JDK error, per http://stackoverflow.com/a/13575810/5295
  "-Djava.util.Arrays.useLegacyMergeSort=true"
) ++ ResourceManagerProperties

val JUnitTestArguments = List(
  //"-q",
  "-v",
  "-s",
  "-a"
  //"--run-listener=org.orbeon.junit.OrbeonJUnitRunListener",
) ++ ResourceManagerProperties

val JunitTestOptions = List(
  libraryDependencies                += "com.novocode" % "junit-interface" % JUnitInterfaceVersion % Test,
  //    libraryDependencies          += "com.lihaoyi" %% "pprint" % "0.4.1" % Test,

  testOptions       in Test          += Tests.Argument(TestFrameworks.JUnit, JUnitTestArguments: _*),
  testOptions       in Test          += Tests.Filter(s ⇒ s.endsWith("Test")),
  testOptions       in Test          += Tests.Filter(s ⇒ s.endsWith("Test") && ! s.contains("CombinedClientTest")),
  parallelExecution in Test          := false,
  fork              in Test          := true, // "By default, tests executed in a forked JVM are executed sequentially"
  javaOptions       in Test          ++= TestJavaOptions,
  baseDirectory     in Test          := baseDirectory.value / ".."
)

// This is copied from the sbt source but doesn't seem to be exported publicly
def myFindUnmanagedJars(config: Configuration, base: File, filter: FileFilter, excl: FileFilter): Classpath = {
  (base * (filter -- excl) +++ (base / config.name).descendantsExcept(filter, excl)).classpath
}

def copyJarFile(sourceJarFile: File, destination: String, excludes: String ⇒ Boolean, matchRawJarName: Boolean) = {

  val sourceJarNameOpt = Some(sourceJarFile.name) collect {
    case MatchRawJarNameRE(name) if matchRawJarName ⇒ name
    case MatchJarNameRE(name)                       ⇒ name
  }

  sourceJarNameOpt flatMap { sourceJarName ⇒

    val targetJarFile = new File(destination + '/' + sourceJarName + ".jar")

    if (! sourceJarFile.name.contains("_sjs")        &&
        ! excludes(sourceJarName)                    &&
        (! targetJarFile.exists || sourceJarFile.lastModified > targetJarFile.lastModified)) {
      println(s"Copying JAR ${sourceJarFile.name} to ${targetJarFile.absolutePath}.")
      IO.copy(List(sourceJarFile → targetJarFile), overwrite = false, preserveLastModified = false)
      Some(targetJarFile)
    } else {
      None
    }
  }
}

def copyFilesToExplodedWarLib(files: Seq[Attributed[File]]): Unit =
  files map (_.data) foreach { file ⇒
    copyJarFile(file, ExplodedWarLibPath, _ contains "scalajs-", matchRawJarName = false)
  }

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

lazy val DatabaseTest = config("db")         extend Test
lazy val DebugTest    = config("debug-test") extend Test

lazy val commonSettings = Seq(
  organization                  := "org.orbeon",
  version                       := orbeonVersionFromProperties.value,
  scalaVersion                  := ScalaVersion,

  jsEnv                         := JSDOMNodeJSEnv().value,

  javacOptions                  ++= Seq(
    "-encoding", "utf8",
    "-source", "1.6",
    "-target", "1.6"
  ),
  scalacOptions                 ++= Seq(
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

  libraryDependencies           += "org.scalactic" %%% "scalactic" % "3.0.0" % Test,
  libraryDependencies           += "org.scalatest" %%% "scalatest" % "3.0.0" % Test,

  unmanagedBase                      := baseDirectory.value / "lib",

  copyJarToExplodedWar          := copyJarFile((packageBin in Compile).value, ExplodedWarLibPath, JarFilesToExcludeFromWar.contains _, matchRawJarName = true),
  copyJarToLiferayWar           := copyJarFile((packageBin in Compile).value, LiferayWarLibPath,  JarFilesToExcludeFromLiferayWar.contains _, matchRawJarName = true)
)

lazy val common = (crossProject.crossType(CrossType.Full) in file("common"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-common"
  )
  .jvmSettings(
    unmanagedBase                      := baseDirectory.value / ".." / ".." / "lib",

    (unmanagedJars in Compile)         := myFindUnmanagedJars(
      Runtime,
      unmanagedBase.value,
      (includeFilter in unmanagedJars).value,
      (excludeFilter in unmanagedJars).value
    )
  )
  .jsSettings(
  )

lazy val commonJVM = common.jvm
lazy val commonJS  = common.js

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
    name := "orbeon-xupdate"
  )

lazy val dom = (project in file("dom"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-dom"
  )

lazy val embedding = (project in file("embedding"))
  .dependsOn(core)
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-embedding"
  )
  .settings(
  )

lazy val fullPortlet = (project in file("full-portlet"))
  .dependsOn(portletSupport)
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-full-portlet"
  )
  .settings(
    libraryDependencies                += "org.joda"      %  "joda-convert"      % JodaConvertVersion % Provided
  )

lazy val formRunnerProxyPortlet = (project in file("proxy-portlet"))
  .dependsOn(portletSupport)
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-proxy-portlet"
  )

lazy val portletSupport = (project in file("portlet-support"))
  .dependsOn(embedding)
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-portlet-support"
  )

lazy val formRunner = (project in file("form-runner"))
  .dependsOn(portletSupport, core % "test->test;compile->compile")
  .configs(DatabaseTest, DebugTest)
  .settings(commonSettings: _*)
  .settings(inConfig(DatabaseTest)(Defaults.testSettings): _*)
  .settings(inConfig(DebugTest)(Defaults.testSettings): _*)
  .settings(
    name := "orbeon-form-runner"
  )
  .settings(JunitTestOptions: _*)
  .settings(
    scalaSource       in DebugTest     := baseDirectory.value / "src" / "test" / "scala",
    javaSource        in DebugTest     := baseDirectory.value / "src" / "test" / "java",
    resourceDirectory in DebugTest     := baseDirectory.value / "src" / "test" / "resources",

    javaOptions       in DebugTest     += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
  )

lazy val formBuilder = (project in file("form-builder"))
  .dependsOn(
    formBuilderSharedJVM,
    formRunner % "test->test;compile->compile",
    core       % "test->test;compile->compile"
  )
  .enablePlugins(SbtCoffeeScript)
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-form-builder"
  )
  .settings(JunitTestOptions: _*)
  .settings(
    parallelExecution in Test          := false,
    fork              in Test          := true,
    javaOptions       in Test          ++= TestJavaOptions,
    baseDirectory     in Test          := baseDirectory.value / ".."
  )

lazy val core = (project in file("src"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(SbtCoffeeScript)
  .dependsOn(commonJVM, dom, xupdate)
  .configs(DebugTest)
  .settings(commonSettings: _*)
  .settings(inConfig(DebugTest)(Defaults.testSettings): _*)
  .settings(
    name                               := "orbeon-core",

    buildInfoPackage                   := "org.orbeon.oxf.common",
    buildInfoKeys                      := Seq[BuildInfoKey](
      "orbeonVersion" → orbeonVersionFromProperties.value,
      "orbeonEdition" → orbeonEditionFromProperties.value
    ),

    defaultConfiguration               := Some(Compile),

    scalaSource       in Compile       := baseDirectory.value / "main" / "scala",
    javaSource        in Compile       := baseDirectory.value / "main" / "java",
    resourceDirectory in Compile       := baseDirectory.value / "main" / "resources",
    sourceDirectory   in Assets        := baseDirectory.value / "main" / "assets"

//    // http://www.scala-sbt.org/0.13/docs/Mapping-Files.html
//    mappings          in (Compile, packageBin) ~= { _ filterNot { case (_, path) ⇒ PathsToExcludeFromCoreJAR.exists(path.startsWith) } }
  )
  .settings(JunitTestOptions: _*)
  .settings(
    scalaSource       in Test          := baseDirectory.value / "test" / "scala",
    javaSource        in Test          := baseDirectory.value / "test" / "java",
    resourceDirectory in Test          := baseDirectory.value / "test" / "resources"
  )
  .settings(
    scalaSource       in DebugTest     := baseDirectory.value / "test" / "scala",
    javaSource        in DebugTest     := baseDirectory.value / "test" / "java",
    resourceDirectory in DebugTest     := baseDirectory.value / "test" / "resources",

    javaOptions       in DebugTest     += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
  ).settings(
    libraryDependencies                += "org.parboiled" %% "parboiled-scala"   % Parboiled1Version,
    libraryDependencies                += "io.spray"      %% "spray-json"        % SprayJsonVersion,
    libraryDependencies                += "joda-time"     %  "joda-time"         % JodaTimeVersion,
    libraryDependencies                += "org.joda"      %  "joda-convert"      % JodaConvertVersion % Provided
//    libraryDependencies                += "javax.servlet" %  "javax.servlet-api" % ServletApiVersion  % Provided,
//    libraryDependencies                += "javax.portlet" %  "portlet-api"       % PortletApiVersion  % Provided
  ).settings(

    unmanagedBase                      := baseDirectory.value / ".." / "lib",

    (unmanagedJars in Runtime)         := myFindUnmanagedJars(
      Runtime,
      unmanagedBase.value,
      (includeFilter in unmanagedJars).value,
      (excludeFilter in unmanagedJars).value
    ),

    (unmanagedJars in Compile)         := (unmanagedJars in Runtime).value ++ myFindUnmanagedJars(
      Compile,
      unmanagedBase.value,
      (includeFilter in unmanagedJars).value,
      (excludeFilter in unmanagedJars).value
    ) ++ myFindUnmanagedJars(
      Provided,
      unmanagedBase.value,
      (includeFilter in unmanagedJars).value,
      (excludeFilter in unmanagedJars).value
    ),

    (unmanagedJars in Test)             := (unmanagedJars in Compile).value ++ myFindUnmanagedJars(
      Test,
      unmanagedBase.value,
      (includeFilter in unmanagedJars).value,
      (excludeFilter in unmanagedJars).value
    )
  )

lazy val root = (project in file("."))
  .aggregate(
    commonJVM,
    commonJS,
    dom,
    formBuilderSharedJVM,
    xupdate,
    core,
    formRunner,
    formBuilder,
    formBuilderClient,
    embedding,
    portletSupport,
    formRunnerProxyPortlet,
    fullPortlet
  )
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

    copyDependenciesToExplodedWar      := {

      // This seems overly complicated but some libraries under `lib/provided` end up in the classpath
      // even though we override `unmanagedJars` above. So we filter `lib/provided` for now.
      def isLibProvided(f: Attributed[File]) =
        f.data.absolutePath.contains("/lib/provided/")

      // Also we would like to find the dependency for all aggregated projects but we don't know how
      // to do this so for specify individual projects.
      copyFilesToExplodedWarLib((dependencyClasspath in Runtime in core).value filterNot isLibProvided)
      copyFilesToExplodedWarLib((dependencyClasspath in Runtime in formBuilder).value filterNot isLibProvided)
    }
  )

sound.play(compile in Compile, Sounds.Blow, Sounds.Basso)
