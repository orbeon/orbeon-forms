package org.orbeon.sbt

import com.github.difflib.UnifiedDiffUtils
import sbt.*
import sbt.Keys.*

import java.io.{File, FileInputStream, FileOutputStream}
import java.util.jar.{JarEntry, JarInputStream, JarOutputStream}
import java.util.regex.Pattern
import scala.annotation.tailrec
import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.Using


object WebJarPatcher {

  case class JarPatch(
    jarPathPattern  : String,
    pathInJarPattern: String,
    replacement     : String => String
  ) {
    def matchesJarPath(jarFile: File): Boolean = jarFile.getAbsolutePath.matches(jarPathPattern)
    def matchesPathInJar(pathInJar: String): Boolean = pathInJar.matches(pathInJarPattern)
  }

  val patchWebJars  = taskKey[Seq[File]]("Apply patches to WebJar files")

  // Patched JAR files are stored in a separate directory with a distinctive prefix
  private def patchesDir     = Def.task((ThisBuild / baseDirectory).value / "webjars-patches")
  private def patchedJarsDir = Def.task((ThisBuild / baseDirectory).value / "src" / "target" / "patched-webjars")

  private def patchedJarFile(originalJar: File, patchDir: File): File =
    patchDir / s"patched-${originalJar.getName}"

  def compilePatchingSettings: Seq[Setting[?]] = Seq(
    patchWebJars                  := cachedPatchWebJarsTask.value,
    Compile / compile             := (Compile / compile).dependsOn(patchWebJars).value,
    Compile / dependencyClasspath := classpathWithPatchedJarFiles((Compile / dependencyClasspath).value, patchedJarsDir.value)
  )

  // webappPrepareTask uses Runtime / fullClasspath
  def runtimeFullClasspathSettings: Seq[Setting[?]] = Seq(
    Runtime / fullClasspath := classpathWithPatchedJarFiles((Runtime / fullClasspath).value, patchedJarsDir.value)
  )

  private def patchesFromPatchFiles(rootPatchesDir: File, patchFiles: Seq[File]): Seq[JarPatch] =
    patchFiles.map { patchFile =>

      // If the patch file is [rootPatchesDir]/jquery.fancytree-2.21.0.jar/dist/jquery.fancytree-all.min.js.patch,
      // we'll use:
      //
      //  - jquery.fancytree-2.21.0.jar as the name of the JAR file
      //  - dist/jquery.fancytree-all.min.js as the name of the file in the JAR

      val pathRelativeToRootPatchesDir = rootPatchesDir.toPath.relativize(patchFile.toPath)
      val jarFile                      = pathRelativeToRootPatchesDir.subpath(0, 1).toString
      val pathRelativeToJarDir         = pathRelativeToRootPatchesDir
          .subpath(1, pathRelativeToRootPatchesDir.getNameCount) // Remove root JAR directory
          .toString
          .replace(File.separator, "/")                          // Make sure we always use / as a path separator
          .stripSuffix(".patch")

      JarPatch(
        jarPathPattern   = s".+/${Pattern.quote(jarFile)}",
        pathInJarPattern = s".+/${Pattern.quote(pathRelativeToJarDir)}",
        replacement      = (originalContent: String) => {

          // Read and apply the .patch file
          val patchLines    = Using(Source.fromFile(patchFile))(_.getLines().toList).get
          val originalLines = originalContent.linesIterator.toList
          val patchedLines  = UnifiedDiffUtils.parseUnifiedDiff(patchLines.asJava).applyTo(originalLines.asJava).asScala

          patchedLines.mkString("\n")
        }
      )
    }

  private def cachedPatchWebJarsTask: Def.Initialize[Task[Seq[File]]] = Def.task {

    val jarFiles       = update.value.allFiles // All resolved dependencies
    val rootPatchesDir = patchesDir.value
    val patchFiles     = (rootPatchesDir ** "*.patch").get
    val cacheDirectory = streams.value.cacheDirectory / "patched-webjars-cache"
    val destinationDir = patchedJarsDir.value

    // Generate patch definitions from patch files
    val patches = patchesFromPatchFiles(rootPatchesDir, patchFiles)

    val cachedFunction = FileFunction.cached(cacheDirectory) { (_: Set[File]) =>
      patchJarFiles(jarFiles, patches, destinationDir).toSet
    }

    // For caching, consider unpatched JAR files and patch files as inputs
    val inputFiles = jarFiles.filter(jarFile => patches.exists(_.matchesJarPath(jarFile))).toSet ++ patchFiles.toSet

    // Generate the files only if they're missing or if any of the input files has changed
    cachedFunction(inputFiles).toSeq
  }

  private def patchJarFiles(
    jarFiles      : Seq[File],
    patches       : Seq[JarPatch],
    destinationDir: File
  ): Seq[File] = {

    if (patches.nonEmpty) {
      IO.createDirectory(destinationDir)
    }

    for {
      jarFile           <- jarFiles
      patchesForJarFile = patches.filter(_.matchesJarPath(jarFile))
      if patchesForJarFile.nonEmpty
    } yield {
      // Apply all patches for this JAR file
      patchJarFile(jarFile, patchesForJarFile, destinationDir)
    }
  }

  private def patchJarFile(originalJar: File, patches: Seq[JarPatch], patchDir: File): File = {
    val patchedJar = patchedJarFile(originalJar, patchDir)
    val jarInput   = new JarInputStream (new FileInputStream (originalJar))
    val jarOutput  = new JarOutputStream(new FileOutputStream(patchedJar))

    try {

      @tailrec
      def patchEntryIfNeeded(entryOpt: Option[JarEntry], patchedFileCount: Int = 0): Int = entryOpt match {
        case None        => patchedFileCount // All entries have been processed
        case Some(entry) =>

          val entryName        = entry.getName                               // We always get / as path separator here
          val patchForEntryOpt = patches.find(_.matchesPathInJar(entryName)) // Support only a single patch for now

          patchForEntryOpt match {
            case Some(patchForEntry) =>

              // Read and patch content
              val originalContent = new String(IO.readBytes(jarInput), "UTF-8")
              val patchedContent  = patchForEntry.replacement(originalContent)

              val newEntry = new JarEntry(entryName)
              newEntry.setTime(entry.getTime)

              jarOutput.putNextEntry(newEntry)
              jarOutput.write(patchedContent.getBytes("UTF-8"))

            case None =>
              // No patching, copy entry
              jarOutput.putNextEntry(new JarEntry(entry))
              IO.transfer(jarInput, jarOutput)
          }

          jarOutput.closeEntry()
          jarInput.closeEntry()

          // Process next entry
          patchEntryIfNeeded(
            entryOpt         = Option(jarInput.getNextJarEntry),
            patchedFileCount = if (patchForEntryOpt.isDefined) patchedFileCount + 1 else patchedFileCount
          )
      }

      // Parse all entries in the JAR file and patch them if needed
      val patchedFileCount = patchEntryIfNeeded(entryOpt = Option(jarInput.getNextJarEntry))

      if (patchedFileCount == 0) {
        // No file could be patched, this is unexpected ⇒ make the build fail
        throw new Exception(s"Could not apply patch to ${originalJar.getName}")
      }

    } finally {
      jarInput.close()
      jarOutput.close()
    }

    patchedJar
  }

  private def classpathWithPatchedJarFiles(classpath: Classpath, patchDir: File): Classpath = {

    // Patched JAR files are kept in a separate directory ⇒ remove matching original JAR files from classpath

    classpath.map { originalFile =>
      val patchedFile = patchedJarFile(originalFile.data, patchDir)
      if (patchedFile.exists()) Attributed.blank(patchedFile) else originalFile
    }
  }
}