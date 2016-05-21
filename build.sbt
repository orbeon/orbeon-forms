val ScalaVersion         = "2.11.8"
val ScalaJsDomVersion    = "0.9.0"
val ScalaJsJQueryVersion = "0.9.0"

val OrbeonFormsVersion   = "2016.2-SNAPSHOT"

val ExplodedWarWebInf             = "build/orbeon-war/WEB-INF"
val ExplodedWarClassesPath        = ExplodedWarWebInf + "/classes"
val CompileClassesPath            = "build/classes"
val TestClassesPath               = "build/test-classes"

val CompileClasspathForEnv        = if (sys.props.get("orbeon.env") == Some("dev")) ExplodedWarClassesPath else CompileClassesPath
val OrbeonVersion                 = sys.props.get("orbeon.version") getOrElse OrbeonFormsVersion
val OrbeonEdition                 = sys.props.get("orbeon.edition") getOrElse "CE"

val ExplodedWarResourcesPath      = ExplodedWarWebInf + "/resources"
val FormBuilderResourcesPathInWar = "forms/orbeon/builder/resources"

val ScalaJSFileNameFormat = "((.+)-(fastopt|opt)).js".r

val fastOptJSToExplodedWar   = taskKey[Unit]("Copy fast-optimized JavaScript files to the exploded WAR.")
val fullOptJSToExplodedWar   = taskKey[Unit]("Copy full-optimized JavaScript files to the exploded WAR.")

def copyScalaJSToExplodedWar(sourceFile: File, rootDirectory: File): Unit = {

  val (prefix, optType) =
    sourceFile.name match { case ScalaJSFileNameFormat(_, prefix, optType) ⇒ prefix → optType }

  val launcherName  = s"$prefix-launcher.js"
  val jsdepsName    = s"$prefix-jsdeps.js"
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

  for ((file, newName) ← names)
    IO.copyFile(
      sourceFile           = file,
      targetFile           = targetDir / newName,
      preserveLastModified = true
    )
}

lazy val commonSettings = Seq(
  organization                 := "org.orbeon",
  version                      := OrbeonFormsVersion,
  scalaVersion                 := ScalaVersion,

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
  )
)

lazy val formBuilderShared = (crossProject.crossType(CrossType.Pure) in file("shared"))
  .settings(
    scalaVersion                   := ScalaVersion
  )
  .jvmSettings(
    classDirectory    in Compile := baseDirectory.value.getParentFile.getParentFile / CompileClasspathForEnv
  )
  .jsSettings(
  )

lazy val formBuilderSharedJVM = formBuilderShared.jvm
lazy val formBuilderSharedJS  = formBuilderShared.js

lazy val formBuilder = (project in file("builder"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(formBuilderSharedJS)
  .settings(commonSettings: _*)
  .settings(
    name                           := "form-builder",

    scalaSource         in Compile := baseDirectory.value / "src" / "builder" / "scala",
    javaSource          in Compile := baseDirectory.value / "src" / "builder" / "java",
    resourceDirectory   in Compile := baseDirectory.value / "src" / "builder" / "resources",

    jsDependencies                 += RuntimeDOM,

    libraryDependencies            += "org.scala-js" %%% "scalajs-dom"    % ScalaJsDomVersion,
    libraryDependencies            += "be.doeraene"  %%% "scalajs-jquery" % ScalaJsJQueryVersion,

    skip in packageJSDependencies  := false,

    unmanagedBase                  := baseDirectory.value / "lib",

    persistLauncher     in Compile := true,

    fastOptJSToExplodedWar := copyScalaJSToExplodedWar(
      (fastOptJS in Compile).value.data,
      baseDirectory.value.getParentFile
    ),

    fullOptJSToExplodedWar := copyScalaJSToExplodedWar(
      (fullOptJS in Compile).value.data,
      baseDirectory.value.getParentFile
    )
  )

lazy val common = (project in file("common"))
  .settings(commonSettings: _*)
  .settings(
    name                         := "orbeon-common",
    unmanagedBase                := baseDirectory.value / ".." / "lib",
    classDirectory    in Compile := baseDirectory.value / ".." / CompileClasspathForEnv
  )

lazy val xupdate = (project in file("xupdate"))
  .dependsOn(common)
  .settings(commonSettings: _*)
  .settings(
    name                         := "orbeon-xupdate",
    unmanagedBase                := baseDirectory.value / ".." / "lib",
    classDirectory    in Compile := baseDirectory.value / ".." / CompileClasspathForEnv
  )

lazy val dom4j = (project in file("dom4j"))
  .settings(commonSettings: _*)
  .settings(
    name                         := "orbeon-dom4j",
    classDirectory    in Compile := baseDirectory.value / ".." / CompileClasspathForEnv
  )

//lazy val resourcesPackaged = (project in file("src"))
//  .settings(commonSettings: _*)
//  .settings(
//    name                         := "orbeon-resources-packaged",
//
//    resourceDirectory in Compile := baseDirectory.value / "resources-packaged",
//    target                       := baseDirectory.value / "resources-packaged-target"
//  )

lazy val core = (project in file("src"))
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(common, dom4j, formBuilderSharedJVM, xupdate)
  .settings(commonSettings: _*)
  .settings(
    name                         := "orbeon-core",

    buildInfoKeys                := Seq[BuildInfoKey]("orbeonVersion" → OrbeonVersion, "orbeonEdition" → OrbeonEdition),
    buildInfoPackage             := "org.orbeon.oxf.common",

    defaultConfiguration := Some(Compile),

    scalaSource       in Compile := baseDirectory.value / "main" / "scala",
    javaSource        in Compile := baseDirectory.value / "main" / "java",
    resourceDirectory in Compile := baseDirectory.value / "main" / "resources",

    scalaSource       in Test    := baseDirectory.value / "test" / "scala",
    javaSource        in Test    := baseDirectory.value / "test" / "java",
    resourceDirectory in Test    := baseDirectory.value / "test" / "resources",

    unmanagedBase                := baseDirectory.value / ".." / "lib",

    classDirectory    in Compile := baseDirectory.value / ".." / CompileClasspathForEnv,
    classDirectory    in Test    := baseDirectory.value / ".." / TestClassesPath
  )

lazy val root = (project in file("."))
  .settings(commonSettings: _*)

sound.play(compile in Compile, Sounds.Blow, Sounds.Basso)
