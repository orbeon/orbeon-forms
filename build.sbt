import sbt.Keys._
import org.orbeon.sbt.OrbeonSupport
import org.orbeon.sbt.OrbeonSupport._
import org.orbeon.sbt.OrbeonWebappPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport.jsEnv
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

val DefaultOrbeonFormsVersion     = "2021.1-SNAPSHOT"
val DefaultOrbeonEdition          = "CE"

// Scala libraries for Scala.js only
val ScalaJsDomVersion             = "0.9.8"
val ScalaJsJQueryVersion          = "0.9.6"
val ScribeVersion                 = "2.7.10"
val PerfolationVersion            = "1.1.5"

// Shared Scala libraries
val ScalatTestVersion             = "3.2.9"
val ScalaTestPlusVersion          = "1.0.0-M2"
val CirceVersion                  = "0.13.0"
val EnumeratumVersion             = "1.6.0"
val EnumeratumCirceVersion        = "1.6.0"
val ShapelessVersion              = "2.3.6"
val ScalaXmlVersion               = "2.0.0-M1"
val ScalaAsyncVersion             = "0.10.0"
val Parboiled1Version             = "1.3.1"
val SprayJsonVersion              = "1.3.2" // 1.3.5 converts to `TreeMap` and breaks order in tests
val AutowireVersion               = "0.2.6"
val SbinaryVersion                = "0.5.1"
val RosHttpVersion                = "2.1.0"
val ScalaLoggingVersion           = "3.9.3"
val Log4sVersion                  = "1.8.2"
val ScalaCollectionCompatVersion  = "2.2.0"

// Java libraries
val JUnitInterfaceVersion         = "0.11"
val Slf4jVersion                  = "1.7.30"
val HttpComponentsVersion         = "4.5.13"
val Log4jVersion                  = "1.2.17"
val CommonsIoVersion              = "2.7"
val FlyingSaucerVersion           = "9.1.22"
val TinkVersion                   = "1.5.0"
val JavaMailVersion               = "1.6.2"
val JavaActivationVersion         = "1.2.0"
val AntVersion                    = "1.10.10"

// "Provided" Java libraries
val ServletApiVersion             = "4.0.1"
val PortletApiVersion             = "3.0.1"
val LiferayPortalServiceVersion   = "6.2.5"
val LiferayPortalKernelVersion    = "5.3.0"


val CoreLibraryDependencies = Seq(
  "com.beachape"                %% "enumeratum"                     % EnumeratumVersion,
  "com.beachape"                %% "enumeratum-circe"               % EnumeratumCirceVersion,
  "com.chuusai"                 %% "shapeless"                      % ShapelessVersion,
  "org.parboiled"               %% "parboiled-scala"                % Parboiled1Version,
  "org.scala-sbt"               %% "sbinary"                        % SbinaryVersion,
  "io.spray"                    %% "spray-json"                     % SprayJsonVersion,
  "org.scala-lang.modules"      %% "scala-xml"                      % ScalaXmlVersion,
  "com.typesafe.scala-logging"  %% "scala-logging"                  % ScalaLoggingVersion,
  "org.log4s"                   %% "log4s"                          % Log4sVersion,
  "org.apache.commons"          %  "commons-lang3"                  % "3.12.0",
  "net.sf.ehcache"              %  "ehcache-core"                   % "2.6.11",
  "commons-beanutils"           %  "commons-beanutils"              % "1.9.4",
  "commons-codec"               %  "commons-codec"                  % "1.15",
  "commons-collections"         %  "commons-collections"            % "3.2.2",
  "commons-digester"            %  "commons-digester"               % "2.1",
  "commons-cli"                 %  "commons-cli"                    % "1.4",
  "commons-discovery"           %  "commons-discovery"              % "0.5",
  "commons-fileupload"          %  "commons-fileupload"             % "1.4",
  "commons-io"                  %  "commons-io"                     % CommonsIoVersion,
  "commons-pool"                %  "commons-pool"                   % "1.6",
  "commons-validator"           %  "commons-validator"              % "1.7",
  "org.apache.ant"              %  "ant"                            % AntVersion,
  "org.apache.ant"              %  "ant-jsch"                       % AntVersion,
  "javax.mail"                  % "javax.mail-api"                  % JavaMailVersion,
  "com.sun.mail"                % "javax.mail"                      % JavaMailVersion exclude("javax.activation", "activation"),
  "com.sun.activation" 		% "javax.activation"                % JavaActivationVersion,
  "org.apache.httpcomponents"   % "httpclient"                      % HttpComponentsVersion,
  "javax.enterprise.concurrent" % "javax.enterprise.concurrent-api" % "1.1",
  "org.apache.httpcomponents"   % "httpclient-cache"                % HttpComponentsVersion,
  "org.apache.httpcomponents"   % "httpmime"                        % HttpComponentsVersion,
  "org.apache.httpcomponents"   % "fluent-hc"                       % HttpComponentsVersion,
  "org.apache.httpcomponents"   % "httpcore"                        % "4.4.13",
  "org.slf4j"                   % "jcl-over-slf4j"                  % Slf4jVersion,
  "org.slf4j"                   % "slf4j-api"                       % Slf4jVersion,
  "org.slf4j"                   % "slf4j-log4j12"                   % Slf4jVersion,
  "log4j"                       % "log4j"                           % Log4jVersion,
  "com.jcraft"                  % "jsch"                            % "0.1.55",
  "jcifs"                       % "jcifs"                           % "1.3.17",
  "com.google.crypto.tink"      % "tink"                            % TinkVersion excludeAll (
    ExclusionRule(organization = "com.amazonaws"),
    ExclusionRule(organization = "com.fasterxml.jackson.core")
  ),
  "bsf"                         % "bsf"                             % "2.4.0"           % Test,
  "org.apache.commons"          % "commons-exec"                    % "1.3"             % Test,
  "org.apache.commons"          % "commons-dbcp2"                   % "2.8.0"           % Test,
  "com.google.code.gson"        % "gson"                            % "2.8.6"           % Test,
  "com.google.guava"            % "guava"                           % "30.0-jre"        % Test,
  "org.mockito"                 % "mockito-all"                     % "1.10.19"         % Test,
  "mysql"                       % "mysql-connector-java"            % "8.0.25"          % Test,
  "org.postgresql"              % "postgresql"                      % "42.2.20"         % Test,
  "org.seleniumhq.selenium"     % "selenium-java"                   % "3.141.59"        % Test,
  "org.xhtmlrenderer"           % "flying-saucer-core"              % FlyingSaucerVersion,
  "org.xhtmlrenderer"           % "flying-saucer-pdf"               % FlyingSaucerVersion,
  "com.github.librepdf"         % "openpdf"                         % "1.3.26",
  "org.bouncycastle"            % "bcmail-jdk15on"                  % "1.68", // for `itext`/`openpdf`, also pulls `bcprov` and `bcpkix`
  "com.drewnoakes"              % "metadata-extractor"              % "2.16.0",
  "com.adobe.xmp"               % "xmpcore"                         % "6.1.11",

  "javax.servlet"             %  "javax.servlet-api"              % ServletApiVersion % Provided,
  "javax.portlet"             %  "portlet-api"                    % PortletApiVersion % Provided
) map
  (_.exclude("commons-logging", "commons-logging")) map // because we have jcl-over-slf4j
  (_.exclude("javax.servlet"  , "servlet-api"))         // because `jcifs` depends on this and we want it provided

val ExplodedWarLibPath            = "build/orbeon-war/WEB-INF/lib"
val LiferayWarLibPath             = "/Users/ebruchez/OF/liferay-portal-6.2-ce-ga6/tomcat-7.0.62/webapps/proxy-portlet/WEB-INF/lib"

val LocalResourcesPath            = "resources-local"

val FormBuilderResourcesPathInWar = "forms/orbeon/builder/resources"
val FormRunnerResourcesPathInWar  = "apps/fr/resources"
val XFormsResourcesPathInWar      = "ops/javascript"

val copyJarToExplodedWar           = taskKey[Option[File]]("Copy JAR file to local WEB-INF/lib for development.")
val copyDependenciesToExplodedWar  = taskKey[Unit]("Copy managed library JAR files to WEB-INF/lib.")
val fastOptJSToLocalResources      = taskKey[Unit]("Copy fast-optimized JavaScript files to local resources.")
val fullOptJSToLocalResources      = taskKey[Unit]("Copy full-optimized JavaScript files to local resources.")
val copyJarToLiferayWar            = taskKey[Option[File]]("Copy JAR file to Liferay WEB-INF/lib for development.")

val orbeonVersionFromProperties    = settingKey[String]("Orbeon Forms version from system properties.")
val orbeonEditionFromProperties    = settingKey[String]("Orbeon Forms edition from system properties.")


lazy val scala212 = "2.12.10"
lazy val scala213 = "2.13.1"
lazy val supportedScalaVersions = List(scala212, scala213)

// "ThisBuild is a Scope encompassing all projects"
scalaVersion                in ThisBuild := scala212
organization                in ThisBuild := "org.orbeon"
version                     in ThisBuild := orbeonVersionFromProperties.value
orbeonVersionFromProperties in ThisBuild := sys.props.get("orbeon.version") getOrElse DefaultOrbeonFormsVersion
orbeonEditionFromProperties in ThisBuild := sys.props.get("orbeon.edition") getOrElse DefaultOrbeonEdition
historyPath                 in ThisBuild := Some((target in LocalRootProject).value / ".history")

traceLevel in ThisBuild := 0

// Give a .js or .jvm project's base directory, return the shared assets directory
def sharedAssetsDir(baseDirectory: File) =
  baseDirectory.getParentFile / "shared" / "src" / "main" / "assets"

def copyFilesToExplodedWarLib(files: Seq[Attributed[File]]): Unit =
  files map (_.data) foreach { file =>
    copyJarFile(file, ExplodedWarLibPath, _ contains "scalajs-", matchRawJarName = false)
  }

def scalaJsFiles(sourceFile: File, pathPrefix: String): Seq[(File, String)] = {

  val (prefix, optType) =
    sourceFile.name match { case MatchScalaJSFileNameFormatRE(_, prefix, optType) => prefix -> optType }

  val jsdepsName    = s"$prefix-jsdeps${if (optType == "opt") ".min" else ""}.js"
  val sourceMapName = s"${sourceFile.name}.map"

  val targetPath = pathPrefix + '/' + "scalajs"

  List(
    sourceFile                                 -> s"$prefix.js",
    (sourceFile.getParentFile / jsdepsName)    -> jsdepsName,
    (sourceFile.getParentFile / sourceMapName) -> sourceMapName
  ) map { case (f, p) => f -> (targetPath + '/' + p) }
}

def copyScalaJSToExplodedWar(sourceFile: File, rootDirectory: File, pathPrefix: String): Unit = {

  val targetDir =
    rootDirectory / LocalResourcesPath

  IO.createDirectory(targetDir)

  for {
    (sourceFile, newPath) <- scalaJsFiles(sourceFile, pathPrefix)
    if sourceFile.exists()
  } locally {
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
  "orbeon-war/jvm/src/main/webapp/WEB-INF/resources"
)

def resourceManagerProperties(buildBaseDirectory: File): List[String] = {

  val pkg = "org.orbeon.oxf.resources"

  val props =
    for {
      (dir, i)    <- TestResourceManagerPaths.zipWithIndex
      absoluteDir = buildBaseDirectory / dir
    }
      yield
        s"-Doxf.resources.priority.${i + 1}=$pkg.FilesystemResourceManagerFactory"                   ::
        s"-Doxf.resources.priority.${i + 1}.oxf.resources.filesystem.sandbox-directory=$absoluteDir" ::
        Nil

  s"-Doxf.resources.factory=$pkg.PriorityResourceManagerFactory"                                     ::
    props.flatten                                                                                    :::
    s"-Doxf.resources.priority.${props.size + 1}=$pkg.ClassLoaderResourceManagerFactory"             ::
    Nil
}

def testJavaOptions(buildBaseDirectory: File) =
  List(
    "-ea",
    "-server",
    "-Djava.awt.headless=true",
    "-Xms256m",
    "-Xmx2G",
    // Some code uses the default time zone, which might different on different system, so we need to set it explicitly
    "-Duser.timezone=America/Los_Angeles",
    "-Doxf.resources.common.min-reload-interval=50",
  //  "-Djava.io.tmpdir=build/temp/test",
    // Getting a JDK error, per http://stackoverflow.com/a/13575810/5295
    "-Djava.util.Arrays.useLegacyMergeSort=true"
  ) ++
    resourceManagerProperties(buildBaseDirectory)

def jUnitTestArguments(buildBaseDirectory: File) =
  List(
    //"-q",
    "-v",
    "-s",
    "-a",
    "-oF"
  ) ++
    resourceManagerProperties(buildBaseDirectory)

def jUnitTestOptions =
  List(
    libraryDependencies                += "com.novocode" % "junit-interface" % JUnitInterfaceVersion % Test,

    testOptions       in Test          += Tests.Argument(TestFrameworks.JUnit, jUnitTestArguments((baseDirectory in ThisBuild).value): _*),
    testOptions       in Test          += Tests.Argument(TestFrameworks.ScalaTest, "-oF"),
    testOptions       in Test          += Tests.Filter(s => s.endsWith("Test")),
    testOptions       in Test          += Tests.Filter(s => s.endsWith("Test") && ! s.contains("ClientTest")),
    parallelExecution in Test          := false,
    fork              in Test          := true, // "By default, tests executed in a forked JVM are executed sequentially"
    javaOptions       in Test          ++= testJavaOptions((baseDirectory in ThisBuild).value),
    baseDirectory     in Test          := Path.absolute(baseDirectory.value / "..")
  )

lazy val DebugTest         = config("debug-test") extend Test
lazy val DatabaseTest      = config("db")         extend Test
lazy val DebugDatabaseTest = config("debug-db")   extend Test

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

  jsEnv                         := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv(),

  javacOptions                  ++= Seq(
    "-encoding", "utf8",
    "-source", "1.8",
    "-target", "1.8"
  ),
  scalacOptions                 ++= Seq(
    "-encoding", "utf8",
    "-feature",
    "-language:postfixOps",
    "-language:reflectiveCalls",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-language:existentials",
    "-deprecation"
    // Consider the following flags
//    "-feature",
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
    "org.scalactic"           %%% "scalactic"               % ScalatTestVersion    % Test,
    "org.scalatest"           %%% "scalatest"               % ScalatTestVersion    % Test,
    "org.scala-lang.modules"  %%% "scala-collection-compat" % ScalaCollectionCompatVersion
  ),

  // This is so that assets added to JAR files are made available to dependent projects.
  // Without this, only classes and resources are made available.
  exportJars := true,

  copyJarToExplodedWar := copyJarFile((packageBin in Compile).value, ExplodedWarLibPath, JarFilesToExcludeFromWar.contains, matchRawJarName = true),
  copyJarToLiferayWar  := copyJarFile((packageBin in Compile).value, LiferayWarLibPath,  JarFilesToExcludeFromLiferayWar.contains, matchRawJarName = true)
) ++ unmanagedJarsSettings

lazy val commonScalaJvmSettings = Seq(
  libraryDependencies ++= Seq(
    "org.scalatestplus" %%% "scalatestplus-junit"   % ScalaTestPlusVersion % Test,
    "org.scalatestplus" %%% "scalatestplus-mockito" % ScalaTestPlusVersion % Test,
    "org.scalatestplus" %%% "scalatestplus-selenium" % ScalaTestPlusVersion % Test
  )
)

lazy val commonScalaJsSettings = Seq(

  skip in packageJSDependencies  := false,
  scalaJSLinkerConfig            ~= { _.withSourceMap(false) },

  scalaJSUseMainModuleInitializer in Compile := true,
  scalaJSUseMainModuleInitializer in Test    := false,

  scalacOptions ++= {
    if (scalaJSVersion.startsWith("0.6."))
      List(
        "-P:scalajs:sjsDefinedByDefault",
        "-P:scalajs:suppressExportDeprecations" // see https://www.scala-js.org/news/2018/11/29/announcing-scalajs-0.6.26/
      )
    else
      Nil
  }
)

lazy val assetsSettings = Seq(

  // We require node anyway for Scala.js testing
  JsEngineKeys.engineType               := JsEngineKeys.EngineType.Node,

  // Less
  includeFilter in (Assets, LessKeys.less) := "*.less",
  LessKeys.compress in Assets              := false,

  // Uglify
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
      case (file, path) if includePath(path) => file -> path.substring(FullWebJarPrefix.length)
    }
  }
)

// This project contains utilities with no dependencies. It is mostly cross-JS/JVM platforms, with
// a few exceptions that are JS- or JVM-only. `common` is not a good name. On the other hand,
// `utils` is also very general. Can we find something more telling?
lazy val common = (crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Full) in file("common"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-common",
    libraryDependencies += "com.beachape"           %%% "enumeratum"        % EnumeratumVersion,
    libraryDependencies += "com.beachape"           %%% "enumeratum-circe"  % EnumeratumCirceVersion,
    libraryDependencies += "org.log4s"              %%% "log4s"             % Log4sVersion,
    crossScalaVersions := supportedScalaVersions
  )
  .jvmSettings(commonScalaJvmSettings)
  .jvmSettings(
    (unmanagedJars in Compile) := myFindUnmanagedJars(
      Runtime,
      unmanagedBase.value,
      (includeFilter in unmanagedJars).value,
      (excludeFilter in unmanagedJars).value
    ),
    libraryDependencies += "org.scala-js"           %% "scalajs-stubs" % scalaJSVersion % Provided,
    libraryDependencies += "org.slf4j"              %  "slf4j-api"     % Slf4jVersion,
    libraryDependencies += "org.slf4j"              %  "slf4j-log4j12" % Slf4jVersion
  )
  .jsSettings(commonScalaJsSettings)
  .jsSettings(
    libraryDependencies += "org.scala-lang.modules" %%  "scala-async"  % ScalaAsyncVersion % Provided
  )

lazy val commonJVM = common.jvm
lazy val commonJS  = common.js
//  .enablePlugins(TzdbPlugin)
  .settings(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.0.0",
//    zonesFilter := {(z: String) => z == "America/Los_Angeles"} // Q: See if/how we do this filtering
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.0.0" % Test // for now, get the whole database
  )

// Custom DOM implementation. This must be cross-platform and have no dependencies.
lazy val dom = (crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure) in file("dom"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-dom",
    crossScalaVersions := supportedScalaVersions
  )

lazy val domJVM = dom.jvm.dependsOn(commonJVM)
lazy val domJS  = dom.js.dependsOn(commonJS)

lazy val embedding = (project in file("embedding"))
  .dependsOn(core)
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-embedding",
    libraryDependencies += "javax.servlet" % "javax.servlet-api" % ServletApiVersion % Provided
  )

lazy val fullPortlet = (project in file("full-portlet"))
  .dependsOn(portletSupport)
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-full-portlet",
    libraryDependencies += "javax.portlet"      % "portlet-api"               % PortletApiVersion           % Provided,
    libraryDependencies += "javax.servlet"      % "javax.servlet-api"         % ServletApiVersion           % Provided,
    libraryDependencies += "com.liferay.portal" % "portal-service"            % LiferayPortalServiceVersion % Provided,
    libraryDependencies += "com.liferay.portal" % "com.liferay.portal.kernel" % LiferayPortalKernelVersion  % Provided
  )

lazy val formRunnerProxyPortlet = (project in file("proxy-portlet"))
  .dependsOn(portletSupport)
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-proxy-portlet",
    libraryDependencies += "javax.portlet"      %  "portlet-api"              % PortletApiVersion           % Provided,
    libraryDependencies += "javax.servlet"      % "javax.servlet-api"         % ServletApiVersion           % Provided,
    libraryDependencies += "com.liferay.portal" % "portal-service"            % LiferayPortalServiceVersion % Provided,
    libraryDependencies += "com.liferay.portal" % "com.liferay.portal.kernel" % LiferayPortalKernelVersion  % Provided
  )

lazy val portletSupport = (project in file("portlet-support"))
  .dependsOn(embedding)
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-portlet-support",
    libraryDependencies += "javax.portlet"      %  "portlet-api"              % PortletApiVersion           % Provided,
    libraryDependencies += "javax.servlet"      % "javax.servlet-api"         % ServletApiVersion           % Provided,
    libraryDependencies += "com.liferay.portal" % "portal-service"            % LiferayPortalServiceVersion % Provided,
    libraryDependencies += "com.liferay.portal" % "com.liferay.portal.kernel" % LiferayPortalKernelVersion  % Provided
  )

lazy val formRunner = (crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Full) in file("form-runner"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-form-runner"
  )

lazy val formRunnerJVM = formRunner.jvm
  .dependsOn(
    xformsJVM,
    core      % "test->test;compile->compile",
    xformsJVM % "test->test;compile->compile",
    portletSupport
  )
  .enablePlugins(SbtWeb)
  .settings(assetsSettings: _*)
  .configs(DatabaseTest, DebugDatabaseTest, DebugTest)
  .settings(commonSettings: _*)
  .settings(inConfig(DatabaseTest)(Defaults.testSettings): _*)
  .settings(inConfig(DebugDatabaseTest)(Defaults.testSettings): _*)
  .settings(inConfig(DebugTest)(Defaults.testSettings): _*)
  .settings(commonScalaJvmSettings)
  .settings(jUnitTestOptions: _*)
  .settings(
    sourceDirectory   in DebugTest     := (sourceDirectory in Test).value,
    javaOptions       in DebugTest     += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",

    sourceDirectory   in DebugDatabaseTest := (sourceDirectory in DatabaseTest).value,
    javaOptions       in DebugDatabaseTest += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
  ).settings(
    libraryDependencies += "javax.servlet" % "javax.servlet-api" % ServletApiVersion  % Provided,
    libraryDependencies += "javax.portlet" %  "portlet-api"      % PortletApiVersion  % Provided,

    libraryDependencies                ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % CirceVersion)
  )
  .settings(
    // Settings here as `.jvmSettings` above causes infinite recursion
    // Package Scala.js output into `orbeon-form-runner.jar`
    // This stores the optimized version. For development we need something else.
    (mappings in packageBin in Compile) ++= scalaJsFiles((fullOptJS in Compile in formRunnerJS).value.data, FormRunnerResourcesPathInWar)
  )

lazy val formRunnerJS = formRunner.js
  .dependsOn(
    commonJS,
    xformsJS % "test->test;compile->compile",
    webFacades
  )
  .settings(commonScalaJsSettings)
  .settings(

    libraryDependencies            ++= Seq(
      "org.scala-js"           %%% "scalajs-dom"    % ScalaJsDomVersion,
      "be.doeraene"            %%% "scalajs-jquery" % ScalaJsJQueryVersion,
      "org.scala-lang.modules" %%% "scala-xml"      % ScalaXmlVersion,
      "io.github.cquiroz" %%% "scala-java-time" % "2.0.0"
    ),

    jsDependencies                 += "org.webjars" % "jquery" % "1.12.0" / "1.12.0/jquery.js",

    jsDependencies      in Test    += ProvidedJS / "ops/javascript/orbeon/util/jquery-orbeon.js" dependsOn "jquery.js",

    // HACK: Not sure why `xformsJS % "test->test;compile->compile"` doesn't expose this.
    unmanagedResourceDirectories in Test += sharedAssetsDir((baseDirectory in xformsJS).value),

    fastOptJSToLocalResources := copyScalaJSToExplodedWar(
      (fastOptJS in Compile).value.data,
      (baseDirectory in ThisBuild).value,
      FormRunnerResourcesPathInWar
    ),

    fullOptJSToLocalResources := copyScalaJSToExplodedWar(
      (fullOptJS in Compile).value.data,
      (baseDirectory in ThisBuild).value,
      FormRunnerResourcesPathInWar
    )
  )

lazy val formBuilder = (crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Full) in file("form-builder"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-form-builder"
  )

lazy val formBuilderJVM = formBuilder.jvm
  .enablePlugins(SbtWeb)
  .dependsOn(
    commonJVM,
    formRunnerJVM % "test->test;compile->compile",
    core          % "test->test;compile->compile"
  )
  .settings(commonScalaJvmSettings)
  .settings(jUnitTestOptions: _*)
  .settings(assetsSettings: _*)
  .settings(
    // Settings here as `.jvmSettings` above causes infinite recursion
    // Package Scala.js output into `orbeon-form-builder.jar`
    // This stores the optimized version. For development we need something else.
    (mappings in packageBin in Compile) ++= scalaJsFiles((fullOptJS in Compile in formBuilderJS).value.data, FormBuilderResourcesPathInWar)
  )


lazy val formBuilderJS = formBuilder.js
  .dependsOn(
    commonJS,
    xformsJS % "test->test;compile->compile",
    formRunnerJS
  )
  .settings(commonScalaJsSettings)
  .settings(

    libraryDependencies            ++= Seq(
      "org.scala-js" %%% "scalajs-dom"    % ScalaJsDomVersion,
      "be.doeraene"  %%% "scalajs-jquery" % ScalaJsJQueryVersion
    ),

    jsDependencies                 += "org.webjars" % "jquery" % "1.12.0" / "1.12.0/jquery.js",

    jsDependencies      in Test    += ProvidedJS / "ops/javascript/orbeon/util/jquery-orbeon.js" dependsOn "jquery.js",

    test in Test := {},

    fastOptJSToLocalResources := copyScalaJSToExplodedWar(
      (fastOptJS in Compile).value.data,
      (baseDirectory in ThisBuild).value,
      FormBuilderResourcesPathInWar
    ),

    fullOptJSToLocalResources := copyScalaJSToExplodedWar(
      (fullOptJS in Compile).value.data,
      (baseDirectory in ThisBuild).value,
      FormBuilderResourcesPathInWar
    )
  )

lazy val xforms = (crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Full) in file("xforms"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-xforms",

    libraryDependencies += "com.lihaoyi" %%% "autowire"    % AutowireVersion,
    libraryDependencies += "com.outr"    %%% "scribe"      % ScribeVersion,
    libraryDependencies += "com.outr"    %%% "perfolation" % PerfolationVersion, // to avoid dependency on `scala-java-locales`

    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % CirceVersion)
  )

lazy val xformsJVM = xforms.jvm
  .dependsOn(
    commonJVM,
    xformsCommonJVM,
    core % "test->test;compile->compile"
  )
  .enablePlugins(SbtWeb)
  .settings(assetsSettings: _*)
  .settings(commonScalaJvmSettings)
  .settings(jUnitTestOptions: _*)
  .settings(

    libraryDependencies += "javax.servlet" % "javax.servlet-api" % ServletApiVersion % Provided,

    // Because `Assets` doesn't check the `shared` directory
    unmanagedResourceDirectories in Assets += sharedAssetsDir(baseDirectory.value),

    // Package Scala.js output into `orbeon-xforms.jar`
    // This stores the optimized version. For development we need something else.
    (mappings in packageBin in Compile) ++= scalaJsFiles((fullOptJS in Compile in xformsJS).value.data, XFormsResourcesPathInWar)
  )

lazy val xformsJS = xforms.js
  .dependsOn(commonJS % "test->test;compile->compile")
  .dependsOn(xformsCommonJS)
  .settings(commonScalaJsSettings)
  .settings(

    libraryDependencies            ++= Seq(
      "org.scala-js" %%% "scalajs-dom"      % ScalaJsDomVersion,
      "be.doeraene"  %%% "scalajs-jquery"   % ScalaJsJQueryVersion,
      "com.beachape" %%% "enumeratum"       % EnumeratumVersion,
      "com.beachape" %%% "enumeratum-circe" % EnumeratumCirceVersion,
      "io.github.cquiroz" %%% "scala-java-time" % "2.0.0"
    ),

    jsDependencies                 += "org.webjars" % "jquery" % "1.12.0" / "1.12.0/jquery.js",

    // Because `jsDependencies` searches in `resources` instead of `assets`, expose the shared `assets` directory
    unmanagedResourceDirectories in Test += sharedAssetsDir(baseDirectory.value),

    jsDependencies      in Test    += ProvidedJS / "ops/javascript/orbeon/util/jquery-orbeon.js" dependsOn "jquery.js",

//    jsEnv                         := NodeJSEnv().value,

    fastOptJSToLocalResources := copyScalaJSToExplodedWar(
      (fastOptJS in Compile).value.data,
      (baseDirectory in ThisBuild).value,
      XFormsResourcesPathInWar
    ),

    fullOptJSToLocalResources := copyScalaJSToExplodedWar(
      (fullOptJS in Compile).value.data,
      (baseDirectory in ThisBuild).value,
      XFormsResourcesPathInWar
    )
  )

lazy val xformsCommon = (crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Full) in file("xforms-common"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-xforms-common"
  )

lazy val xformsCommonJVM = xformsCommon.jvm
  .dependsOn(commonJVM)
  .dependsOn(domJVM)
  .dependsOn(coreCrossPlatformJVM)

lazy val xformsCommonJS = xformsCommon.js
  .dependsOn(commonJS)
  .dependsOn(domJS)
  .dependsOn(coreCrossPlatformJS)
  .settings(commonScalaJsSettings)

lazy val xformsRuntime = (crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Full) in file("xforms-runtime"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-xforms-runtime"
  )

lazy val xformsRuntimeJVM = xformsRuntime.jvm
  .dependsOn(
    commonJVM,
    core % "test->test;compile->compile"
  )

lazy val xformsRuntimeJS = xformsRuntime.js
  .dependsOn(commonJS % "test->test;compile->compile")
  .dependsOn(dom.js)
  .settings(commonScalaJsSettings)

lazy val fileScanExample = (project in file("file-scan-example"))
  .dependsOn(xformsJVM)
  .settings(commonSettings: _*)
  .settings(
    name := "file-scan-example"
  )

lazy val nodeFacades = (project in file("node-facades"))
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-node-facades",

    parallelExecution               in Test := false,
    scalaJSUseMainModuleInitializer in Test := false,
    jsEnv                           in Test := new org.scalajs.jsenv.nodejs.NodeJSEnv(),

    scalaJSLinkerConfig                     ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
  )

lazy val webFacades = (project in file("web-facades"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(commonJS)
  .settings(commonSettings: _*)
  .settings(commonScalaJsSettings: _*)
  .settings(
    name := "orbeon-web-facades",

    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom"      % ScalaJsDomVersion,
      "be.doeraene"  %%% "scalajs-jquery"   % ScalaJsJQueryVersion
    )
  )

lazy val coreCrossPlatform = (crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Full) in file("core-cross-platform"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-core-cross-platform"
  )

lazy val coreCrossPlatformJVM = coreCrossPlatform.jvm
  .dependsOn(commonJVM)
  .dependsOn(domJVM)
  .settings(
    libraryDependencies                ++= CoreLibraryDependencies
  )

lazy val coreCrossPlatformJS = coreCrossPlatform.js
  .dependsOn(commonJS)
  .dependsOn(domJS)
  .settings(commonScalaJsSettings)

lazy val core = (project in file("src"))
  .enablePlugins(BuildInfoPlugin, SbtWeb)
  .dependsOn(
    coreCrossPlatformJVM,
    commonJVM,
    domJVM
  )
  .configs(DebugTest)
  .settings(commonSettings: _*)
  .settings(commonScalaJvmSettings)
  .settings(inConfig(DebugTest)(Defaults.testSettings): _*)
  .settings(assetsSettings: _*)
  .settings(
    name                               := "orbeon-core",

    buildInfoPackage                   := "org.orbeon.oxf.common",
    buildInfoKeys                      := Seq[BuildInfoKey](
      "orbeonVersion" -> orbeonVersionFromProperties.value,
      "orbeonEdition" -> orbeonEditionFromProperties.value
    ),

    crossScalaVersions                 := Nil,
    defaultConfiguration               := Some(Compile),
    sourceDirectory in ThisProject     := baseDirectory.value // until we have a more standard layout
  )
  .settings(jUnitTestOptions: _*)
  .settings(
    javaOptions       in DebugTest     += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
    libraryDependencies                ++= CoreLibraryDependencies
  )

lazy val orbeonWar = (crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Dummy) in file("orbeon-war"))
  .settings(
    name := "orbeon-war",
    exportJars := false
  )

lazy val orbeonWarJVM = orbeonWar.jvm
  .dependsOn(
    commonJVM,
    domJVM,
    core,
    xformsJVM,
    formRunnerJVM,
    formBuilderJVM,
    embedding,              // probably unneeded in WAR
    portletSupport,
    formRunnerProxyPortlet, // unneeded in WAR, but `proxy-portlet-jar` task for now uses JAR in WAR
    fullPortlet
  )
  .settings(OrbeonWebappPlugin.projectSettings: _*)
  .settings(commonSettings: _*)
  .settings(
    exportJars := false
  )


lazy val orbeonWarJS = orbeonWar.js
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings: _*)
  .dependsOn(
    commonJS,
    xformsJS,
    nodeFacades
  )
  .settings(

    // Poor man's way to pass parameters to the test suite
    buildInfoPackage               := "org.orbeon.fr",
    buildInfoObject                := "TestParametersFromSbt",
    buildInfoKeys                  := Seq[BuildInfoKey](
      "baseDirectory" -> (baseDirectory  in ThisBuild).value.getAbsolutePath
    ),

    libraryDependencies            ++= Seq(
      "org.scala-js"           %%% "scalajs-dom"    % ScalaJsDomVersion,
      "be.doeraene"            %%% "scalajs-jquery" % ScalaJsJQueryVersion,
      "fr.hmil"                %%% "roshttp"        % RosHttpVersion    % Test,
      "org.scala-lang.modules" %%  "scala-async"    % ScalaAsyncVersion % Provided
    ),

    parallelExecution               in Test := false,
    scalaJSUseMainModuleInitializer in Test := false,
    jsEnv                           in Test := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    scalaJSLinkerConfig                     ~= { _.withModuleKind(ModuleKind.CommonJSModule) },

    testOptions                     in Test +=
      Tests.Setup(() => OrbeonSupport.dummyDependency((Keys.`package` in orbeonWarJVM).value))
  )


lazy val root = (project in file("."))
  .aggregate(
    commonJVM,
    commonJS,
    domJVM,
    core,
    xformsJVM,
    xformsJS,
    formRunnerJVM,
    formRunnerJS,
    formBuilderJVM,
    formBuilderJS,
    embedding,
    portletSupport,
    formRunnerProxyPortlet,
    fullPortlet,
    orbeonWarJVM,
    orbeonWarJS
  )
  .settings(
    // TEMP: override so that root project doesn't search under src
    sourceDirectory in ThisProject     := baseDirectory.value / "root", // until we have a more standard layout
    publishArtifact                    := false,
    crossScalaVersions                 := Nil // "crossScalaVersions must be set to Nil on the aggregating project"
  )
