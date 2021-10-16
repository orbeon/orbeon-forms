package org.orbeon.sbt

import sbt.FilesInfo.{exists, lastModified}
import sbt.Keys._
import sbt.{Def, _}

object Compat {

  import sbt._
  import sbt.Keys._
  import sbt.util.CacheStoreFactory
  import sbt.util.FileInfo.Style
  import sbt.util.FileFunction.UpdateFunction
  import OrbeonWebappPlugin.autoImport.webappPrepare
  import sbt.util.CacheImplicits._

  def cached(storeFactory: CacheStoreFactory, inStyle: Style, outStyle: Style)(action: UpdateFunction): Set[File] => Set[File] =
    FileFunction.cached(
      storeFactory = storeFactory,
      inStyle      = inStyle,
      outStyle     = outStyle)(
      action       = action
    )
}

object OrbeonSupport {

  val MatchScalaJSFileNameFormatRE = """((.+)-(fastopt|opt)).js""".r
  val MatchJarNameRE               = """(.+)\.jar""".r
  val MatchRawJarNameRE            = """([^_]+)(?:_.*)?\.jar""".r

  def dummyDependency(value: Any): Unit = ()

  // This is copied from the sbt source but doesn't seem to be exported publicly
  def myFindUnmanagedJars(config: Configuration, base: File, filter: FileFilter, excl: FileFilter): Classpath = {
    (base * (filter -- excl) +++ (base / config.name).descendantsExcept(filter, excl)).classpath
  }

  val FileIsMinifiedVersionFilter = new SimpleFileFilter(f => {
      val path   = f.absolutePath
      val prefix = path.substring(0, path.length - ".js".length)

      def endsWithMin  = Seq("-min.js", ".min.js") exists path.endsWith
      def existsSource = new File(prefix + "_src.js").exists

      endsWithMin || existsSource
    }
  )

  val FileHasNoMinifiedVersionFilter = new SimpleFileFilter(f => {
      val path   = f.absolutePath
      val prefix = path.substring(0, path.length - ".js".length)

      def hasNoMin           = Seq("-min.js", ".min.js") forall (suffix => ! new File(prefix + suffix).exists)
      def isNotSourceWithMin = ! (path.endsWith("_src.js") && new File(path.substring(0, path.length - "_src.js".length) + ".js").exists)

      hasNoMin && isNotSourceWithMin
    }
  )

  def copyJarFile(sourceJarFile: File, destination: String, excludes: String => Boolean, matchRawJarName: Boolean): Option[File] = {

    val sourceJarNameOpt = Some(sourceJarFile.name) collect {
      case MatchRawJarNameRE(name) if matchRawJarName => name
      case MatchJarNameRE(name)                       => name
    }

    sourceJarNameOpt flatMap { sourceJarName =>

      val targetJarFile = new File(destination + '/' + sourceJarName + ".jar")

      if (! sourceJarFile.name.contains("_sjs")        &&
          ! excludes(sourceJarName)                    &&
          (! targetJarFile.exists || sourceJarFile.lastModified > targetJarFile.lastModified)) {
        println(s"Copying JAR ${sourceJarFile.name} to ${targetJarFile.absolutePath}.")
        IO.copy(List(sourceJarFile -> targetJarFile), overwrite = false, preserveLastModified = false, preserveExecutable = true)
        Some(targetJarFile)
      } else {
        None
      }
    }
  }
}

// Custom version of `xsbt-web-plugin`'s `WebappPlugin` by Earl Douglas under  BSD-3-Clause-license
object OrbeonWebappPlugin {

  import OrbeonSupport._

  object autoImport {
    lazy val webappPrepare       = taskKey[Seq[(File, String)]]("prepare webapp contents for packaging")
    lazy val webappPostProcess   = taskKey[File => Unit]("additional task after preparing the webapp")
    lazy val webappWebInfClasses = settingKey[Boolean]("use WEB-INF/classes instead of WEB-INF/lib")
  }

  import autoImport._

  def projectSettings: Seq[Setting[_]] =
    Seq(
      webappPrepare / sourceDirectory  := (Compile / sourceDirectory).value / "webapp",
      webappPrepare / target           := (Compile / target).value / "webapp",
      webappPrepare                    := webappPrepareTask.value,
      webappPostProcess                := { _ => () },
      webappWebInfClasses              := false,
      watchSources ++= ((webappPrepare / sourceDirectory).value ** "*").get
    ) ++
      Defaults.packageTaskSettings(Keys.`package`, webappPrepare)

  private def webappPrepareTask: Def.Initialize[Task[Seq[(File, String)]]] = Def.task {

    def cacheify(name: String, dest: File => Option[File], in: Set[File]): Set[File] =
      Compat.cached(streams.value.cacheStoreFactory /  "xsbt-orbeon-web-plugin" / name, lastModified, exists)({
        (inChanges, outChanges) =>
          // toss out removed files
          for {
            removed  <- inChanges.removed
            toRemove <- dest(removed)
          } locally {
            IO.delete(toRemove)
          }

          // apply and report changes
          for {
            in  <- inChanges.added ++ inChanges.modified -- inChanges.removed
            out <- dest(in)
            _   = IO.copyFile(in, out)
          } yield
            out
      }).apply(in)

    val webappSrcDir = (webappPrepare / sourceDirectory).value
    val webappTarget = (webappPrepare / target).value

    val classpath    = (Runtime / fullClasspath).value
    val webInfDir    = webappTarget / "WEB-INF"
    val webappLibDir = webInfDir / "lib"

    cacheify(
      "webapp",
      { in =>
        for {
          f <- Some(in)
          if ! f.isDirectory
          r <- IO.relativizeFile(webappSrcDir, f)
        } yield
          IO.resolve(webappTarget, r)
      },
      (webappSrcDir ** "*").get.toSet
    )

    val thisArtifact = (Compile / packageBin / packagedArtifact).value._1

    // The following is a lot by trial and error. We assume `exportJars := true` in projects. `fullClasspath` then contains
    // only JAR files. From there, we collect those which are "artifacts". This includes our own artifacts, but also managed
    // dependencies (but not unmanaged ones it seems). To discriminate, we find that our own artifacts contain the `Compile`
    // configuration.

    val onlyJars =
      for {
        item         <- classpath.toList
        if ! item.data.isDirectory
      } yield
        item

    val candidates =
      for {
        item         <- onlyJars
        artifactOpt  = item.metadata.entries collectFirst {
          case AttributeEntry(key, value: Artifact) if value.configurations.to[Set].contains(Compile) => value
        }
      } yield
        item -> artifactOpt

    val (compiled, notCompiled) =
      candidates.partition(_._2.isDefined)

    for {
      (item, artifactOpt) <- compiled
      artifact            <- artifactOpt
      if artifact != thisArtifact
    } locally {
      IO.copyFile(item.data, webappLibDir / (artifact.name + ".jar"))
    }

    val providedClasspath =
      myFindUnmanagedJars(Provided,
        unmanagedBase.value,
        (unmanagedJars / includeFilter).value,
        (unmanagedJars / excludeFilter).value
      )

    val providedJars = providedClasspath.to[List].map(_.data).to[Set]

    cacheify(
      "lib-deps",
      { in => Some(webappTarget / "WEB-INF" / "lib" / in.getName) },
      // Include non-compiled dependencies but exclude "provided" JARs
      notCompiled.map(_._1.data).to[Set] -- providedJars
    )

    webappPostProcess.value(webappTarget)

    (webappTarget ** "*") pair (Path.relativeTo(webappTarget) | Path.flat)
  }

}