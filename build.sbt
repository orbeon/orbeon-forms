import sbt.Keys._
import org.orbeon.sbt.OrbeonSupport
import org.orbeon.sbt.OrbeonSupport._
import org.orbeon.sbt.OrbeonWebappPlugin
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

// For our GitHub packages
resolvers += Resolver.githubPackages("orbeon")
githubOwner       := "orbeon"
githubTokenSource := TokenSource.Environment("REPO_GITHUB_TOKEN") || TokenSource.Environment("GITHUB_TOKEN")

ThisBuild / evictionErrorLevel := Level.Info

// For now, keep ad-hoc list for offline client
val TimezonesToInclude = Set(
  "America/Los_Angeles",
  "Asia/Kolkata",
  "Asia/Calcutta", // deprecated
)

val includeTimezone: String => Boolean = tz =>
  TimezonesToInclude(tz) || tz.startsWith("Australia/")

val DefaultOrbeonFormsVersion     = "2021.1-SNAPSHOT"
val DefaultOrbeonEdition          = "CE"

// Scala libraries for Scala.js only
val SaxonJsVersion                   = "10.0.0.64-SNAPSHOT"
val XercesVersion                    = "2.11.0.11-SNAPSHOT"
val SaxVersion                       = "2.0.2.8-SNAPSHOT"
val ScalaJsDomVersion                = "1.2.0"
val ScalaJsJQueryVersion             = "0.9.6"
val ScribeVersion                    = "2.7.13"
val PerfolationVersion               = "1.1.7"
val ScalaJsStubsVersion              = "1.0.0" // can be different from Scala.js version
val ScalaJsFakeWeakReferencesVersion = "1.0.0" // switch to `scalajs-weakreferences` when browser support is there
val ScalaJsTimeVersion               = "2.0.0"
val ScalaJsLocalesVersion            = "1.1.0"

// Scala libraries for Scala JVM only
val Parboiled1Version             = "1.3.1"
val ScalaLoggingVersion           = "3.9.4"

// Shared Scala libraries
val ScalaTestVersion              = "3.1.4"
val CirceVersion                  = "0.13.0"
val EnumeratumVersion             = "1.7.0"
val EnumeratumCirceVersion        = "1.7.0"
val ShapelessVersion              = "2.3.4"
val ScalaXmlVersion               = "2.0.0"  // see https://github.com/orbeon/orbeon-forms/issues/4927
val ScalaAsyncVersion             = "0.10.0" // "1.0.0" with `-Xasync` causes issues
//val ScalaAsyncVersion             = "1.0.1"
val Parboiled2Version             = "2.2.1"
val AutowireVersion               = "0.3.2"
val ScalatagsVersion              = "0.9.4"
val SbinaryVersion                = "0.5.1"
val Log4sVersion                  = "1.10.0"
val ScalaCollectionCompatVersion  = "2.2.0"

// Java libraries
val SaxonJvmVersion               = "9.1.0.8.3"
val JUnitInterfaceVersion         = "0.11"
val Slf4jVersion                  = "1.7.32"
val HttpComponentsVersion         = "4.5.13"
val Log4j2Version                 = "2.17.0"
val CommonsIoVersion              = "2.11.0"
val FlyingSaucerVersion           = "9.1.22"
val TinkVersion                   = "1.6.1"
val JavaMailVersion               = "1.6.2"
val JavaActivationVersion         = "1.2.0"
val AntVersion                    = "1.10.11"

// "Provided" Java libraries
val ServletApiVersion             = "4.0.1"
val PortletApiVersion             = "3.0.1"
val LiferayPortalServiceVersion   = "6.2.5"
val LiferayPortalKernelVersion    = "5.3.0"


val CoreLibraryDependencies = Seq(
  "org.orbeon"                  % "saxon"                           % SaxonJvmVersion, // Java library!
  "com.beachape"                %% "enumeratum"                     % EnumeratumVersion,
  "com.beachape"                %% "enumeratum-circe"               % EnumeratumCirceVersion,
  "com.chuusai"                 %% "shapeless"                      % ShapelessVersion,
  "org.parboiled"               %% "parboiled-scala"                % Parboiled1Version,
  "org.parboiled"               %% "parboiled"                      % Parboiled2Version,
  "org.scala-sbt"               %% "sbinary"                        % SbinaryVersion,
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
  "com.sun.activation"          % "javax.activation"                % JavaActivationVersion,
  "org.apache.httpcomponents"   % "httpclient"                      % HttpComponentsVersion,
  "javax.enterprise.concurrent" % "javax.enterprise.concurrent-api" % "1.1",
  "org.apache.httpcomponents"   % "httpclient-cache"                % HttpComponentsVersion,
  "org.apache.httpcomponents"   % "httpmime"                        % HttpComponentsVersion,
  "org.apache.httpcomponents"   % "fluent-hc"                       % HttpComponentsVersion,
  "org.apache.httpcomponents"   % "httpcore"                        % "4.4.14",
  "org.slf4j"                   % "jcl-over-slf4j"                  % Slf4jVersion,
  "org.slf4j"                   % "slf4j-api"                       % Slf4jVersion,
  "org.apache.logging.log4j"    % "log4j-slf4j-impl"                % Log4j2Version, // move to `log4j-slf4j18-impl` for SLF4J 1.8.x releases or newer; seems like 1.8 is dead and replaced by 2.0; but that's still alpha as of 2021-12
  "org.apache.logging.log4j"    % "log4j-api"                       % Log4j2Version,
  "org.apache.logging.log4j"    % "log4j-core"                      % Log4j2Version,
  "org.apache.logging.log4j"    % "log4j-1.2-api"                   % Log4j2Version, // for eXist JARs
  "com.jcraft"                  % "jsch"                            % "0.1.55",
  "jcifs"                       % "jcifs"                           % "1.3.17",
  "com.google.crypto.tink"      % "tink"                            % TinkVersion excludeAll (
    ExclusionRule(organization = "com.amazonaws"),
    ExclusionRule(organization = "com.fasterxml.jackson.core")
  ),
  "bsf"                         % "bsf"                             % "2.4.0"           % Test,
  "org.apache.commons"          % "commons-exec"                    % "1.3"             % Test,
  "org.apache.commons"          % "commons-dbcp2"                   % "2.9.0"           % Test,
  "com.google.code.gson"        % "gson"                            % "2.8.8"           % Test,
  "com.google.guava"            % "guava"                           % "30.0-jre"        % Test,
  "org.mockito"                 % "mockito-all"                     % "1.10.19"         % Test,
  "mysql"                       % "mysql-connector-java"            % "8.0.26"          % Test,
  "org.postgresql"              % "postgresql"                      % "42.2.24"         % Test,
  "org.seleniumhq.selenium"     % "selenium-java"                   % "3.141.59"        % Test,
  "org.xhtmlrenderer"           % "flying-saucer-core"              % FlyingSaucerVersion,
  "org.xhtmlrenderer"           % "flying-saucer-pdf"               % FlyingSaucerVersion,
  "com.github.librepdf"         % "openpdf"                         % "1.3.26",
  "org.bouncycastle"            % "bcmail-jdk15on"                  % "1.69", // for `openpdf`, also pulls `bcprov` and `bcpkix`
  "com.drewnoakes"              % "metadata-extractor"              % "2.16.0",
  "com.adobe.xmp"               % "xmpcore"                         % "6.1.11",

  "javax.servlet"               %  "javax.servlet-api"              % ServletApiVersion % Provided,
  "javax.portlet"               %  "portlet-api"                    % PortletApiVersion % Provided
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


lazy val scala212 = "2.12.15"
lazy val scala213 = "2.13.4"
lazy val supportedScalaVersions = List(scala212, scala213)

// "ThisBuild is a Scope encompassing all projects"
ThisBuild / scalaVersion                := scala212
ThisBuild / organization                := "org.orbeon"
ThisBuild / version                     := orbeonVersionFromProperties.value
ThisBuild / orbeonVersionFromProperties := sys.props.get("orbeon.version") getOrElse DefaultOrbeonFormsVersion
ThisBuild / orbeonEditionFromProperties := sys.props.get("orbeon.edition") getOrElse DefaultOrbeonEdition
ThisBuild / historyPath                 := Some((LocalRootProject / target).value / ".history")
ThisBuild / traceLevel                  := 0

// Restrict the number of concurrent linker processes so we don't run out of memory
Global / concurrentRestrictions += Tags.limit(ScalaJSTags.Link, 1)
Global / concurrentRestrictions += Tags.exclusive(ScalaJSTags.Link)
Assets / concurrentRestrictions += Tags.exclusive(Tags.All)
TestAssets / concurrentRestrictions += Tags.exclusive(Tags.All)
Global / parallelExecution := false

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

    Test / testOptions                 += Tests.Argument(TestFrameworks.JUnit, jUnitTestArguments((ThisBuild / baseDirectory).value): _*),
    Test / testOptions                 += Tests.Argument(TestFrameworks.ScalaTest, "-oF"),
    Test / testOptions                 += Tests.Filter(s => s.endsWith("Test")),
    Test / testOptions                 += Tests.Filter(s => s.endsWith("Test") && ! s.contains("ClientTest")),
    Test / parallelExecution           := false,
    Test / fork                        := true, // "By default, tests executed in a forked JVM are executed sequentially"
    Test / javaOptions                 ++= testJavaOptions((ThisBuild / baseDirectory).value),
    Test / baseDirectory               := Path.absolute(baseDirectory.value / "..")
  )

lazy val DebugTest         = config("debug-test") extend Test
lazy val DatabaseTest      = config("db")         extend Test
lazy val DebugDatabaseTest = config("debug-db")   extend Test

lazy val unmanagedJarsSettings = Seq(

  unmanagedBase                     := (ThisBuild / baseDirectory).value / "lib",

  (Runtime / unmanagedJars)         := myFindUnmanagedJars(
    Runtime,
    unmanagedBase.value,
    (unmanagedJars / includeFilter).value,
    (unmanagedJars / excludeFilter).value
  ),

  (Compile / unmanagedJars)         := (Runtime / unmanagedJars).value ++ myFindUnmanagedJars(
    Compile,
    unmanagedBase.value,
    (unmanagedJars / includeFilter).value,
    (unmanagedJars / excludeFilter).value
  ) ++ myFindUnmanagedJars(
    Provided,
    unmanagedBase.value,
    (unmanagedJars / includeFilter).value,
    (unmanagedJars / excludeFilter).value
  ),

  (Test / unmanagedJars)             := (Compile / unmanagedJars).value ++ myFindUnmanagedJars(
    Test,
    unmanagedBase.value,
    (unmanagedJars / includeFilter).value,
    (unmanagedJars / excludeFilter).value
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
    "-deprecation",
//    "-Xasync",
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
    "org.scalactic"           %%% "scalactic"               % ScalaTestVersion    % Test,
    "org.scalatest"           %%% "scalatest"               % ScalaTestVersion    % Test,
    "org.scala-lang.modules"  %%% "scala-collection-compat" % ScalaCollectionCompatVersion
  ),

  // This is so that assets added to JAR files are made available to dependent projects.
  // Without this, only classes and resources are made available.
  exportJars := true,

  copyJarToExplodedWar := copyJarFile((Compile / packageBin).value, ExplodedWarLibPath, JarFilesToExcludeFromWar.contains, matchRawJarName = true),
  copyJarToLiferayWar  := copyJarFile((Compile / packageBin).value, LiferayWarLibPath,  JarFilesToExcludeFromLiferayWar.contains, matchRawJarName = true)
) ++ unmanagedJarsSettings

lazy val commonScalaJvmSettings = Seq(
  libraryDependencies ++= Seq(
    "org.scalatestplus" %%% "junit-4-12"     % "3.2.2.0" % Test,
    "org.scalatestplus" %%% "mockito-1-10"   % "3.1.0.0" % Test,
    "org.scalatestplus" %%% "selenium-3-141" % "3.2.2.0" % Test
  )
)

lazy val commonScalaJsSettings = Seq(

  packageJSDependencies / skip   := false,
  scalaJSLinkerConfig            ~= { _.withSourceMap(false) },
//  scalaJSLinkerConfig in (Compile, fullOptJS) ~= { _.withParallel(false) },

//  scalaJSLinkerConfig ~= { _.withOptimizer(false) },//XXX TMP

  Compile / scalaJSUseMainModuleInitializer := true,
  Test    / scalaJSUseMainModuleInitializer := false,

  scalacOptions ++= {
    if (scalaJSVersion.startsWith("0.6."))
      List(
        "-P:scalajs:sjsDefinedByDefault"
      )
    else
      Nil
  }
)

lazy val assetsSettings = Seq(

  // We require node anyway for Scala.js testing
  JsEngineKeys.engineType               := JsEngineKeys.EngineType.Node,

  // Less
  Assets / LessKeys.less / includeFilter   := "*.less",
  Assets / LessKeys.compress               := false,

  // Uglify
  Assets / pipelineStages                  := Seq(uglify),

  // Minify all JavaScript files which are not minified/debug and which don't already have a minified version
  // NOTE: The default `excludeFilter in uglify` explicitly excludes files under `resourceDirectory in Assets`.
  uglify / includeFilter                   := (uglify / includeFilter).value && FileHasNoMinifiedVersionFilter && -FileIsMinifiedVersionFilter,
  uglify / excludeFilter                   := (uglify / excludeFilter).value || HiddenFileFilter || "*-debug.js", // || ((baseDirectory).value / "src" ** "codemirror-*" / "bin"),
  uglifyCompressOptions                    := Seq("warnings=false"),

  // By default sbt-web places resources under META-INF/resources/webjars. We don't support this yet so we fix it back.
  // Also filter out a few things.
  Assets / WebKeys.exportedMappings := {

    val FullWebJarPrefix = s"${org.webjars.WebJarAssetLocator.WEBJARS_PATH_PREFIX}/${moduleName.value}/${version.value}/"

    def includePath(path: String) = {

      val lastPath = path.split("/").last
      val ext      = IO.split(lastPath)._2

      ext != "" && ext != "less" && path.startsWith(FullWebJarPrefix)
    }

    (Assets / WebKeys.exportedMappings).value collect {
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
    (Compile / unmanagedJars) := myFindUnmanagedJars(
      Runtime,
      unmanagedBase.value,
      (unmanagedJars / includeFilter).value,
      (unmanagedJars / excludeFilter).value
    ),
    libraryDependencies += "org.scala-js"             %% "scalajs-stubs"   % ScalaJsStubsVersion % Provided,
    libraryDependencies += "org.slf4j"                %  "slf4j-api"       % Slf4jVersion,
    libraryDependencies += "org.apache.logging.log4j" % "log4j-slf4j-impl" % Log4j2Version,
  )
  .jsSettings(commonScalaJsSettings)
  .jsSettings(
    libraryDependencies += "org.scala-lang.modules" %%  "scala-async"  % ScalaAsyncVersion,
    Compile / unmanagedJars := Nil
  )

lazy val commonJVM = common.jvm
lazy val commonJS  = common.js
  .enablePlugins(TzdbPlugin)
  .settings(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time"       % ScalaJsTimeVersion,
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time-tzdb"  % ScalaJsTimeVersion,
    zonesFilter := { (z: String) => includeTimezone(z) },
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-locales"    % ScalaJsLocalesVersion,
    libraryDependencies += "io.github.cquiroz" %%% "locales-minimal-en-db" % ScalaJsLocalesVersion
  )

// Custom DOM implementation. This must be cross-platform and have no dependencies.
lazy val dom = (crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure) in file("dom"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-dom",
    crossScalaVersions := supportedScalaVersions
  )
  .jsSettings(
    libraryDependencies += "org.scala-js" %%% "scalajs-fake-weakreferences" % ScalaJsFakeWeakReferencesVersion
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
    portletSupport,
    formRunnerCommonJVM

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
    DebugTest / sourceDirectory         := (Test / sourceDirectory).value,
    DebugTest / javaOptions             += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",

    DebugDatabaseTest / sourceDirectory := (DatabaseTest / sourceDirectory).value,
    DebugDatabaseTest / javaOptions     += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
  ).settings(
    libraryDependencies += "javax.servlet" % "javax.servlet-api" % ServletApiVersion  % Provided,
    libraryDependencies += "javax.portlet" %  "portlet-api"      % PortletApiVersion  % Provided,

    libraryDependencies                ++= Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % CirceVersion)
  )
  .settings(
    // Settings here as `.jvmSettings` above causes infinite recursion
    // Package Scala.js output into `orbeon-form-runner.jar`
    // This stores the optimized version. For development we need something else.
    (Compile / packageBin / mappings) ++= scalaJsFiles((formRunnerWeb / Compile / fullOptJS).value.data, FormRunnerResourcesPathInWar),
  )

lazy val formRunnerJS = formRunner.js
  .dependsOn(
    xformsRuntimeJS,
    formRunnerCommonJS,
    formRunnerWeb % "test->compile"
  )
  .settings(commonScalaJsSettings)
  .enablePlugins(JSDependenciesPlugin)
  .settings(
    Compile / unmanagedJars      := Nil,
    Compile / unmanagedClasspath := Nil
  )
  .settings(

    Compile / scalaJSUseMainModuleInitializer := false,

    libraryDependencies            ++= Seq(
      "org.scala-js"           %%% "scalajs-dom"     % ScalaJsDomVersion,
      "be.doeraene"            %%% "scalajs-jquery"  % ScalaJsJQueryVersion,
      "org.scala-lang.modules" %%% "scala-xml"       % ScalaXmlVersion,
      "io.github.cquiroz"      %%% "scala-java-time" % ScalaJsTimeVersion,
    ),

    jsDependencies                      += "org.webjars" % "jquery" % "3.6.0" / "3.6.0/jquery.js",
    Test / jsDependencies               += ProvidedJS / "ops/javascript/orbeon/util/jquery-orbeon.js" dependsOn "jquery.js",
    Test / unmanagedResourceDirectories += (xformsWeb / baseDirectory).value / "src" / "main" / "assets",

    fastOptJSToLocalResources := copyScalaJSToExplodedWar(
      (Compile / fastOptJS).value.data,
      (ThisBuild / baseDirectory).value,
      FormRunnerResourcesPathInWar
    ),

    fullOptJSToLocalResources := copyScalaJSToExplodedWar(
      (Compile / fullOptJS).value.data,
      (ThisBuild/ baseDirectory).value,
      FormRunnerResourcesPathInWar
    )
  )

lazy val formRunnerCommon = (crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure) in file("form-runner-common"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-form-runner-common"
  )

lazy val formRunnerCommonJVM = formRunnerCommon.jvm
  .dependsOn(
    commonJVM,
    xformsCommonJVM
  )

lazy val formRunnerCommonJS = formRunnerCommon.js
  .dependsOn(
    commonJS,
    xformsCommonJS
  )
  .settings(commonScalaJsSettings)
  .settings(
    Compile / unmanagedJars      := Nil,
    Compile / unmanagedClasspath := Nil
  )
  .settings(

    libraryDependencies            ++= Seq(
      "org.scala-js"           %%% "scalajs-dom"     % ScalaJsDomVersion,
      "be.doeraene"            %%% "scalajs-jquery"  % ScalaJsJQueryVersion,
      "org.scala-lang.modules" %%% "scala-xml"       % ScalaXmlVersion,
      "io.github.cquiroz"      %%% "scala-java-time" % "2.0.0"
    ),

    fastOptJSToLocalResources := copyScalaJSToExplodedWar(
      (Compile / fastOptJS).value.data,
      (ThisBuild / baseDirectory).value,
      FormRunnerResourcesPathInWar
    ),

    fullOptJSToLocalResources := copyScalaJSToExplodedWar(
      (Compile / fullOptJS).value.data,
      (ThisBuild/ baseDirectory).value,
      FormRunnerResourcesPathInWar
    )
  )

lazy val formRunnerWeb = (project in file("form-runner-web"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-form-runner-web"
  )
  .dependsOn(
    commonJS,
    xformsWeb % "test->test;compile->compile",
    webFacades,
    formRunnerCommonJS
  )
  .settings(commonScalaJsSettings)
  .enablePlugins(JSDependenciesPlugin)
  .settings(

    libraryDependencies            ++= Seq(
      "org.scala-js"           %%% "scalajs-dom"     % ScalaJsDomVersion,
      "be.doeraene"            %%% "scalajs-jquery"  % ScalaJsJQueryVersion,
      "org.scala-lang.modules" %%% "scala-xml"       % ScalaXmlVersion,
      "io.github.cquiroz"      %%% "scala-java-time" % "2.0.0"
    ),

    fastOptJSToLocalResources := copyScalaJSToExplodedWar(
      (Compile / fastOptJS).value.data,
      (ThisBuild / baseDirectory).value,
      FormRunnerResourcesPathInWar
    ),

    fullOptJSToLocalResources := copyScalaJSToExplodedWar(
      (Compile / fullOptJS).value.data,
      (ThisBuild/ baseDirectory).value,
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
    (Compile / packageBin / mappings) ++= scalaJsFiles((formBuilderJS / Compile / fullOptJS).value.data, FormBuilderResourcesPathInWar)
  )


lazy val formBuilderJS = formBuilder.js
  .dependsOn(
    commonJS,
    xformsWeb % "test->test;compile->compile",
    formRunnerWeb
  )
  .settings(commonScalaJsSettings)
  .settings(

    libraryDependencies            ++= Seq(
      "org.scala-js" %%% "scalajs-dom"    % ScalaJsDomVersion,
      "be.doeraene"  %%% "scalajs-jquery" % ScalaJsJQueryVersion
    ),

    Test / test := {},

    fastOptJSToLocalResources := copyScalaJSToExplodedWar(
      (Compile / fastOptJS).value.data,
      (ThisBuild / baseDirectory).value,
      FormBuilderResourcesPathInWar
    ),

    fullOptJSToLocalResources := copyScalaJSToExplodedWar(
      (Compile / fullOptJS).value.data,
      (ThisBuild / baseDirectory).value,
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
    xformsRuntimeJVM,
    xformsCommonJVM,
    core % "test->test;compile->compile"
  )
  .enablePlugins(SbtWeb)
  .settings(assetsSettings: _*)
  .settings(commonScalaJvmSettings)
  .settings(jUnitTestOptions: _*)
  .settings(

    libraryDependencies += "javax.servlet" % "javax.servlet-api" % ServletApiVersion % Provided,

    // Add the path to the assets from the `xformsWeb` project so that they get processed in this project instead
    Assets / unmanagedSourceDirectories += (xformsWeb / baseDirectory).value / "src" / "main" / "assets",

    // Package Scala.js output into `orbeon-xforms.jar`
    // This stores the optimized version. For development we need something else.
    (Compile / packageBin / mappings) ++= scalaJsFiles((xformsWeb     / Compile / fullOptJS).value.data, XFormsResourcesPathInWar),
  )

lazy val xformsJS = xforms.js
  .dependsOn(
    commonJS % "test->test;compile->compile",
    xformsCommonJS
  )
  .settings(commonScalaJsSettings)
  .settings(

    libraryDependencies            ++= Seq(
      "org.scala-js" %%% "scalajs-dom"      % ScalaJsDomVersion,
      "be.doeraene"  %%% "scalajs-jquery"   % ScalaJsJQueryVersion,
      "com.beachape" %%% "enumeratum"       % EnumeratumVersion,
      "com.beachape" %%% "enumeratum-circe" % EnumeratumCirceVersion,
      "io.github.cquiroz" %%% "scala-java-time" % "2.0.0"
    ),

    fastOptJSToLocalResources := copyScalaJSToExplodedWar(
      (Compile / fastOptJS).value.data,
      (ThisBuild / baseDirectory).value,
      XFormsResourcesPathInWar
    ),

    fullOptJSToLocalResources := copyScalaJSToExplodedWar(
      (Compile / fullOptJS).value.data,
      (ThisBuild / baseDirectory).value,
      XFormsResourcesPathInWar
    )
  )

lazy val xformsCommon = (crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Full) in file("xforms-common"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-xforms-common",
    libraryDependencies += "com.chuusai" %% "shapeless" % ShapelessVersion,
    libraryDependencies += "com.lihaoyi" %%% "autowire" % AutowireVersion
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scala212,
      compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
    )
  )

lazy val xformsCommonJVM = xformsCommon.jvm
  .dependsOn(
    commonJVM,
    domJVM,
    core,
    coreCrossPlatformJVM // implied
  )

lazy val xformsCommonJS = xformsCommon.js
  .dependsOn(
    commonJS,
    domJS,
    coreCrossPlatformJS
  )
  .settings(commonScalaJsSettings)
  .settings(
    Compile / unmanagedJars      := Nil,
    Compile / unmanagedClasspath := Nil
  )

lazy val xformsAnalysis = (crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Full) in file("xforms-analysis"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-xforms-analysis",

    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % CirceVersion)
  )

lazy val xformsAnalysisJVM = xformsAnalysis.jvm
  .dependsOn(
    xformsCommonJVM
  )

lazy val xformsAnalysisJS = xformsAnalysis.js
  .dependsOn(
    xformsCommonJS
  )
  .settings(commonScalaJsSettings)
  .settings(
    Compile / unmanagedJars      := Nil,
    Compile / unmanagedClasspath := Nil
  )

lazy val xformsCompiler = (crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Full) in file("xforms-compiler"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-xforms-compiler",

    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % CirceVersion)
  )

lazy val xformsCompilerJVM = xformsCompiler.jvm
  .dependsOn(
    xformsAnalysisJVM,
    core % "test->test;compile->compile",
    coreCrossPlatformJVM // implied
  )
  .settings(jUnitTestOptions: _*)
  .settings(
    libraryDependencies += compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" % Test cross CrossVersion.full)
  )

lazy val xformsCompilerJS = xformsCompiler.js
  .dependsOn(
    xformsAnalysisJS,
    coreCrossPlatformJS
  )
  .settings(commonScalaJsSettings)

lazy val xformsRuntime = (crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Full) in file("xforms-runtime"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-xforms-runtime",

    libraryDependencies += "com.lihaoyi"            %%% "autowire"  % AutowireVersion,
    libraryDependencies += "org.scala-lang.modules" %%% "scala-xml" % ScalaXmlVersion,
    libraryDependencies += "org.parboiled"          %%% "parboiled" % Parboiled2Version,

    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % CirceVersion)
  )

lazy val xformsRuntimeJVM = xformsRuntime.jvm
  .dependsOn(
    xformsCompilerJVM, // "real" compiler is only on JVM side
    xformsAnalysisJVM,
    core,
    coreCrossPlatformJVM // implied
  )

lazy val xformsRuntimeJS = xformsRuntime.js
  .dependsOn(
    xformsCompilerJS, // stubs and shared stuff
    xformsAnalysisJS,
    coreCrossPlatformJS
  )
  .settings(commonScalaJsSettings)
  .settings(
    Compile / unmanagedJars      := Nil,
    Compile / unmanagedClasspath := Nil
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scala212,
      compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
    )
  )

lazy val xformsWeb = (project in file("xforms-web"))
  .settings(commonSettings: _*)
  .settings(commonScalaJsSettings)
  .enablePlugins(ScalaJSPlugin)
  .enablePlugins(JSDependenciesPlugin)
  .dependsOn(
    commonJS % "test->test;compile->compile",
    xformsCommonJS
  )
  .settings(
    Compile / unmanagedJars      := Nil,
    Compile / unmanagedClasspath := Nil
  )
  .settings(
    name := "orbeon-xforms-web",

    libraryDependencies            ++= Seq(
      "com.lihaoyi"            %%% "autowire"         % AutowireVersion,
      "com.lihaoyi"            %%% "scalatags"        % ScalatagsVersion,
      "org.scala-lang.modules" %%% "scala-xml"        % ScalaXmlVersion,
      "com.outr"               %%% "scribe"           % ScribeVersion,
      "com.outr"               %%% "perfolation"      % PerfolationVersion, // to avoid dependency on `scala-java-locales`
      "org.scala-js"           %%% "scalajs-dom"      % ScalaJsDomVersion,
      "be.doeraene"            %%% "scalajs-jquery"   % ScalaJsJQueryVersion,
      "com.beachape"           %%% "enumeratum"       % EnumeratumVersion,
      "com.beachape"           %%% "enumeratum-circe" % EnumeratumCirceVersion,
      "io.github.cquiroz"      %%% "scala-java-time"  % "2.0.0"
    ),

    jsDependencies                      += "org.webjars" % "jquery" % "3.6.0" / "3.6.0/jquery.js",
    Test / jsDependencies               += ProvidedJS / "ops/javascript/orbeon/util/jquery-orbeon.js" dependsOn "jquery.js",
    Test / unmanagedResourceDirectories += baseDirectory.value / "src" / "main" / "assets",


    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % CirceVersion),

    fastOptJSToLocalResources := copyScalaJSToExplodedWar(
      (Compile / fastOptJS).value.data,
      (ThisBuild / baseDirectory).value,
      XFormsResourcesPathInWar
    ),

    fullOptJSToLocalResources := copyScalaJSToExplodedWar(
      (Compile / fullOptJS).value.data,
      (ThisBuild / baseDirectory).value,
      XFormsResourcesPathInWar
    )
  )

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

    Test / parallelExecution                := false,
    Test / scalaJSUseMainModuleInitializer  := false,
    Test / jsEnv                            := new org.scalajs.jsenv.nodejs.NodeJSEnv(),

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
  .settings(
    libraryDependencies ++= Seq(
      "org.parboiled"   %%% "parboiled"     % Parboiled2Version,
      "org.scala-lang"  %   "scala-reflect" % scala212,
      compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),
    ),

    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % CirceVersion)
  )

lazy val coreCrossPlatformJVM = coreCrossPlatform.jvm
  .dependsOn(
    commonJVM,
    domJVM
  )
  .settings(
    libraryDependencies ++= CoreLibraryDependencies
  )

lazy val coreCrossPlatformJS = coreCrossPlatform.js
  .dependsOn(commonJS)
  .dependsOn(domJS)
  .settings(commonScalaJsSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.xml"    %%% "sax"       % SaxVersion,
      "org.orbeon" %%% "saxon"     % SaxonJsVersion,
      "org.orbeon" %%% "xerces"    % XercesVersion,
      "com.chuusai" %% "shapeless" % ShapelessVersion,
    ),
    Compile / unmanagedJars      := Nil,
    Compile / unmanagedClasspath := Nil
  )

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

    ThisProject / sourceDirectory      := baseDirectory.value // until we have a more standard layout
  )
  .settings(jUnitTestOptions: _*)
  .settings(
    DebugTest / javaOptions            += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
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
    xformsWeb,
    nodeFacades
  )
  .settings(

    // Poor man's way to pass parameters to the test suite
    buildInfoPackage               := "org.orbeon.fr",
    buildInfoObject                := "TestParametersFromSbt",
    buildInfoKeys                  := Seq[BuildInfoKey](
      "baseDirectory" -> (ThisBuild / baseDirectory).value.getAbsolutePath
    ),

    libraryDependencies            ++= Seq(
      "org.scala-js"           %%% "scalajs-dom"    % ScalaJsDomVersion,
      "be.doeraene"            %%% "scalajs-jquery" % ScalaJsJQueryVersion,
      "org.scala-lang.modules" %%  "scala-async"    % ScalaAsyncVersion,
      "io.monix"               %%% "monix"          % "3.4.0"
    ),

    Test / parallelExecution                := false,
    Test / scalaJSUseMainModuleInitializer  := false,
    Test / jsEnv                            := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    scalaJSLinkerConfig                     ~= { _.withModuleKind(ModuleKind.CommonJSModule) },

    Test / testOptions                      += {
      val packageFile = (orbeonWarJVM / Keys.`package`).value
      Tests.Setup(() => OrbeonSupport.dummyDependency(packageFile))
    }
  )

lazy val root = (project in file("."))
  .aggregate(
    commonJVM,
    commonJS,
    domJVM,
    core,
    xformsJVM,
    xformsWeb,
    formRunnerJVM,
    formRunnerJS,
    formRunnerWeb,
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
    ThisProject / sourceDirectory := baseDirectory.value / "root", // until we have a more standard layout
    publish                       := {},
    publishLocal                  := {},
    crossScalaVersions            := Nil // "crossScalaVersions must be set to Nil on the aggregating project"
  )
