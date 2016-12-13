import sbt.Keys._
import org.orbeon.sbt.OrbeonSupport._
import org.orbeon.sbt.OrbeonWebappPlugin
//import sbtcross.{crossProject, CrossType} // until Scala.js 1.0.0 is released

val DefaultOrbeonFormsVersion     = "2016.3-SNAPSHOT"
val DefaultOrbeonEdition          = "CE"

val ScalaJsDomVersion             = "0.9.0"
val ScalaJsJQueryVersion          = "0.9.0"
val JUnitInterfaceVersion         = "0.11"
val JodaConvertVersion            = "1.2"
val ServletApiVersion             = "3.0.1"
val PortletApiVersion             = "2.0"
val Slf4jVersion                  = "1.7.21"
val HttpComponentsVersion         = "4.3.5"  // 4.5.2
val Log4jVersion                  = "1.2.17"
val CommonsIoVersion              = "2.0.1"  // 2.5

val CoreLibraryDependencies = Seq(
  "org.parboiled"             %% "parboiled-scala"     % "1.1.7",
  "io.spray"                  %% "spray-json"          % "1.3.2",
  "org.scala-lang.modules"    %% "scala-xml"           % "1.0.6",
  "joda-time"                 %  "joda-time"           % "2.1",
  "org.joda"                  %  "joda-convert"        % JodaConvertVersion % Provided,
  "org.apache.commons"        %  "commons-lang3"       % "3.1",    // 3.5
  "net.sf.ehcache"            %  "ehcache-core"        % "2.6.3",  // 2.6.11
  "commons-beanutils"         %  "commons-beanutils"   % "1.5",    // 1.9.3
  "commons-codec"             %  "commons-codec"       % "1.6",    // 1.10
  "commons-collections"       %  "commons-collections" % "3.2.2",
  "commons-digester"          %  "commons-digester"    % "1.5",    // 2.1
  "commons-cli"               %  "commons-cli"         % "1.0",    // 1.3.1
  "commons-discovery"         %  "commons-discovery"   % "0.4",    // 0.5
  "commons-fileupload"        %  "commons-fileupload"  % "1.2.2",  // 1.3.2
  "commons-io"                %  "commons-io"          % CommonsIoVersion,
  "commons-pool"              %  "commons-pool"        % "1.6",
  "commons-validator"         %  "commons-validator"   % "1.4.0",  // 1.5.1
  "javax.activation"          % "activation"           % "1.1.1",
  "org.apache.httpcomponents" % "httpclient"           % HttpComponentsVersion,
  "org.apache.httpcomponents" % "httpclient-cache"     % HttpComponentsVersion,
  "org.apache.httpcomponents" % "httpmime"             % HttpComponentsVersion,
  "org.apache.httpcomponents" % "fluent-hc"            % HttpComponentsVersion,
  "org.apache.httpcomponents" % "httpcore"             % "4.3.2",
  "org.slf4j"                 % "jcl-over-slf4j"       % Slf4jVersion,
  "org.slf4j"                 % "slf4j-api"            % Slf4jVersion,
  "org.slf4j"                 % "slf4j-log4j12"        % Slf4jVersion,
  "log4j"                     % "log4j"                % Log4jVersion,
  "tyrex"                     % "tyrex"                % "1.0",    // 1.0.1
  "com.jcraft"                % "jsch"                 % "0.1.42", // 0.1.54
  "jcifs"                     % "jcifs"                % "1.3.17",
  "bsf"                       % "bsf"                  % "2.4.0"           % Test,
  "org.apache.commons"        % "commons-exec"         % "1.1"             % Test, // 1.3
  "com.google.code.gson"      % "gson"                 % "2.3.1"           % Test, // 2.8.0
  "com.google.guava"          % "guava"                % "13.0.1"          % Test, // 20.0
  "org.mockito"               % "mockito-all"          % "1.8.5"           % Test, // 1.10.19
  "mysql"                     % "mysql-connector-java" % "5.1.26"          % Test, // 6.0.5,
  "org.postgresql"            % "postgresql"           % "9.3-1102-jdbc4"  % Test,
  "org.seleniumhq.selenium"   % "selenium-java"        % "2.45.0"          % Test  // 3.0.1
//  "javax.servlet"             %  "javax.servlet-api"   % ServletApiVersion % Provided,
//  "javax.portlet"             %  "portlet-api"         % PortletApiVersion % Provided
) map
  (_.exclude("commons-logging", "commons-logging")) map // because we have jcl-over-slf4j
  (_.exclude("javax.servlet"  , "servlet-api"))         // because `jcifs` depends on this and we want it provided

val ExplodedWarWebInf             = "build/orbeon-war/WEB-INF"
val ExplodedWarLibPath            = ExplodedWarWebInf + "/lib"
val LiferayWarLibPath             = "/Users/ebruchez/OF/liferay-portal-6.2-ce-ga6/tomcat-7.0.62/webapps/proxy-portlet/WEB-INF/lib"

val ExplodedWarResourcesPath      = ExplodedWarWebInf + "/resources"
val FormBuilderResourcesPathInWar = "forms/orbeon/builder/resources"

val copyJarToExplodedWar           = taskKey[Option[File]]("Copy JAR file to local WEB-INF/lib for development.")
val copyDependenciesToExplodedWar  = taskKey[Unit]("Copy managed library JAR files to WEB-INF/lib.")
val fastOptJSToExplodedWar         = taskKey[Unit]("Copy fast-optimized JavaScript files to the exploded WAR.")
val fullOptJSToExplodedWar         = taskKey[Unit]("Copy full-optimized JavaScript files to the exploded WAR.")
val copyJarToLiferayWar            = taskKey[Option[File]]("Copy JAR file to Liferay WEB-INF/lib for development.")

val orbeonVersionFromProperties    = settingKey[String]("Orbeon Forms version from system properties.")
val orbeonEditionFromProperties    = settingKey[String]("Orbeon Forms edition from system properties.")

// "ThisBuild is a Scope encompassing all projects"
scalaVersion                in ThisBuild := "2.11.8"
organization                in ThisBuild := "org.orbeon"
version                     in ThisBuild := orbeonVersionFromProperties.value
orbeonVersionFromProperties in ThisBuild := sys.props.get("orbeon.version") getOrElse DefaultOrbeonFormsVersion
orbeonEditionFromProperties in ThisBuild := sys.props.get("orbeon.edition") getOrElse DefaultOrbeonEdition

historyPath                 in ThisBuild := Some((target in LocalRootProject).value / ".history")

def copyFilesToExplodedWarLib(files: Seq[Attributed[File]]): Unit =
  files map (_.data) foreach { file ⇒
    copyJarFile(file, ExplodedWarLibPath, _ contains "scalajs-", matchRawJarName = false)
  }

def scalaJsFiles(sourceFile: File): Seq[(File, String)] = {

  val (prefix, optType) =
    sourceFile.name match { case MatchScalaJSFileNameFormatRE(_, prefix, optType) ⇒ prefix → optType }

  val launcherName  = s"$prefix-launcher.js"
  val jsdepsName    = s"$prefix-jsdeps${if (optType == "opt") ".min" else ""}.js"
  val sourceMapName = s"${sourceFile.name}.map"

  val targetPath = FormBuilderResourcesPathInWar + '/' + "scalajs"

  List(
    sourceFile                                 → s"$prefix.js",
    (sourceFile.getParentFile / launcherName)  → launcherName,
    (sourceFile.getParentFile / jsdepsName)    → jsdepsName,
    (sourceFile.getParentFile / sourceMapName) → sourceMapName
  ) map { case (f, p) ⇒ f → (targetPath + '/' + p) }
}

def copyScalaJSToExplodedWar(sourceFile: File, rootDirectory: File): Unit = {

  val targetDir =
    rootDirectory / ExplodedWarResourcesPath

  IO.createDirectory(targetDir)

  for ((sourceFile, newPath) ← scalaJsFiles(sourceFile)) {
    val targetFile = targetDir / newPath
    println(s"Copying Scala.js file ${sourceFile.name} to ${targetFile.absolutePath}.")
    IO.copyFile(
      sourceFile           = sourceFile,
      targetFile           = targetFile,
      preserveLastModified = true
    )
  }
}

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
  "src/test/resources", // so that Java processor tests work
  "orbeon-war/src/main/webapp/WEB-INF/resources"
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

  testOptions       in Test          += Tests.Argument(TestFrameworks.JUnit, JUnitTestArguments: _*),
  testOptions       in Test          += Tests.Filter(s ⇒ s.endsWith("Test")),
  testOptions       in Test          += Tests.Filter(s ⇒ s.endsWith("Test") && ! s.contains("CombinedClientTest")),
  parallelExecution in Test          := false,
  fork              in Test          := true, // "By default, tests executed in a forked JVM are executed sequentially"
  javaOptions       in Test          ++= TestJavaOptions,
  baseDirectory     in Test          := Path.absolute(baseDirectory.value / "..")
)

lazy val DatabaseTest = config("db")         extend Test
lazy val DebugTest    = config("debug-test") extend Test

lazy val unmanagedJarsSettings = Seq(

  unmanagedBase                      := (baseDirectory in ThisBuild).value / "lib",

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

lazy val commonSettings = Seq(

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

  libraryDependencies ++= Seq(
    "org.scalactic" %%% "scalactic" % "3.0.0" % Test,
    "org.scalatest" %%% "scalatest" % "3.0.0" % Test
  ),

  // This is so that assets added to JAR files are made available to dependent projects.
  // Without this, only classes and resources are made available.
  exportJars := true,

  copyJarToExplodedWar := copyJarFile((packageBin in Compile).value, ExplodedWarLibPath, JarFilesToExcludeFromWar.contains, matchRawJarName = true),
  copyJarToLiferayWar  := copyJarFile((packageBin in Compile).value, LiferayWarLibPath,  JarFilesToExcludeFromLiferayWar.contains, matchRawJarName = true)
) ++ unmanagedJarsSettings

lazy val assetsSettings = Seq(

  // We require node anyway for Scala.js testing
  JsEngineKeys.engineType               := JsEngineKeys.EngineType.Node,

  // Less
  includeFilter in (Assets, LessKeys.less) := "*.less",
  LessKeys.compress in Assets              := false,

  // Uglify
  // NOTE: `sbt-coffeescript` is not `pipelineStages`-aware.
  pipelineStages             in Assets  := Seq(uglify),

  // Minify all JavaScript files which are not minified/debug and which don't already have a minified version
  // NOTE: The default `excludeFilter in uglify` explicitly excludes files under `resourceDirectory in Assets`.
  includeFilter              in uglify  := (includeFilter in uglify).value && FileHasNoMinifiedVersionFilter && -FileIsMinifiedVersionFilter,
  excludeFilter              in uglify  := (excludeFilter in uglify).value || HiddenFileFilter || "*-debug.js",
  UglifyKeys.compressOptions            := Seq("warnings=false"),

  // By default sbt-web places resources under META-INF/resources/webjars. We don't support this yet so we fix it back.
  // Also filter out a few things.
  WebKeys.exportedMappings   in Assets := {

    val FullWebJarPrefix = s"${org.webjars.WebJarAssetLocator.WEBJARS_PATH_PREFIX}/${moduleName.value}/${version.value}/"

    def includePath(path: String) = {

      val lastPath = path.split("/").last
      val ext      = IO.split(lastPath)._2

      ext != "" && ext != "less" && path.startsWith(FullWebJarPrefix)
    }

    (WebKeys.exportedMappings in Assets).value collect {
      case (file, path) if includePath(path) ⇒ file → path.substring(FullWebJarPrefix.length)
    }
  }
)

lazy val common = (crossProject.crossType(CrossType.Full) in file("common"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-common"
  )
  .jvmSettings(
    (unmanagedJars in Compile) := myFindUnmanagedJars(
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

lazy val xupdate = (project in file("xupdate"))
  .dependsOn(commonJVM)
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-xupdate",

    libraryDependencies ++= Seq(
      "commons-io" % "commons-io" % CommonsIoVersion,
      "log4j"      % "log4j"      % Log4jVersion
    ) map (_.exclude("commons-logging", "commons-logging"))
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
    libraryDependencies += "org.joda" % "joda-convert" % JodaConvertVersion % Provided
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
  .enablePlugins(SbtWeb)
  .settings(assetsSettings: _*)
  .configs(DatabaseTest, DebugTest)
  .settings(commonSettings: _*)
  .settings(inConfig(DatabaseTest)(Defaults.testSettings): _*)
  .settings(inConfig(DebugTest)(Defaults.testSettings): _*)
  .settings(JunitTestOptions: _*)
  .settings(
    name := "orbeon-form-runner"
  )
  .settings(
    sourceDirectory   in DebugTest     := (sourceDirectory in Test).value,
    javaOptions       in DebugTest     += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
  ).settings(
    libraryDependencies                += "org.joda"               %  "joda-convert"      % JodaConvertVersion % Provided
  )

lazy val formBuilder = (crossProject.crossType(CrossType.Full) in file("form-builder"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-form-builder"
  )

lazy val formBuilderJVM = formBuilder.jvm
  .enablePlugins(SbtCoffeeScript, SbtWeb)
  .dependsOn(
    commonJVM,
    formRunner % "test->test;compile->compile",
    core       % "test->test;compile->compile"
  )
  .settings(JunitTestOptions: _*)
  .settings(assetsSettings: _*)
  .settings(
    // Settings here as `.jvmSettings` above causes infinite recursion
    // Package Scala.js output into main Form Builder JAR
    // This stores the optimized version. For development we need something else.
    (mappings in packageBin in Compile) ++= scalaJsFiles((fullOptJS in Compile in formBuilderJS).value.data)
  )


lazy val formBuilderJS: Project =formBuilder.js
  .dependsOn(commonJS)
  .settings(

    libraryDependencies            ++= Seq(
      "org.scala-js" %%% "scalajs-dom"    % ScalaJsDomVersion,
      "be.doeraene"  %%% "scalajs-jquery" % ScalaJsJQueryVersion
    ),

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

lazy val core = (project in file("src"))
  .enablePlugins(BuildInfoPlugin, SbtCoffeeScript, SbtWeb)
  .dependsOn(commonJVM, dom, xupdate)
  .configs(DebugTest)
  .settings(commonSettings: _*)
  .settings(inConfig(DebugTest)(Defaults.testSettings): _*)
  .settings(assetsSettings: _*)
  .settings(
    name                               := "orbeon-core",

    buildInfoPackage                   := "org.orbeon.oxf.common",
    buildInfoKeys                      := Seq[BuildInfoKey](
      "orbeonVersion" → orbeonVersionFromProperties.value,
      "orbeonEdition" → orbeonEditionFromProperties.value
    ),

    defaultConfiguration               := Some(Compile),

    sourceDirectory in ThisProject     := baseDirectory.value // until we have a more standard layout
  )
  .settings(JunitTestOptions: _*)
  .settings(
    javaOptions       in DebugTest     += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
    libraryDependencies                ++= CoreLibraryDependencies
  )

lazy val orbeonWar = (project in file("orbeon-war"))
  .settings(
    name := "orbeon-war"
  )
  .dependsOn(
    commonJVM,
    dom,
    xupdate,
    core,
    formRunner,
    formBuilderJVM,
    embedding,              // probably unneeded in WAR
    portletSupport,
    formRunnerProxyPortlet, // unneeded in WAR, but `proxy-portlet-jar` task for now uses JAR in WAR
    fullPortlet
  )
  .settings(OrbeonWebappPlugin.projectSettings: _*)
  .settings(commonSettings: _*)

lazy val root = (project in file("."))
  .aggregate(
    commonJVM,
    commonJS,
    dom,
    xupdate,
    core,
    formRunner,
    formBuilderJVM,
    formBuilderJS,
    embedding,
    portletSupport,
    formRunnerProxyPortlet,
    fullPortlet,
    orbeonWar
  )
  .settings(
    // TEMP: override so that root project doesn't search under src
    sourceDirectory in ThisProject     := baseDirectory.value / "root", // until we have a more standard layout
    publishArtifact                    := false
  )

sound.play(compile in Compile, Sounds.Blow, Sounds.Basso)
