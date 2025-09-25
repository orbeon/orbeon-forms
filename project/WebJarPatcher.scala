import sbt.*
import sbt.Keys.*

import java.io.{File, FileInputStream, FileOutputStream}
import java.util.jar.{JarEntry, JarInputStream, JarOutputStream}
import scala.annotation.tailrec

object WebJarPatcher {

  private val patches = Seq(
    Patch(
      jarPathPattern   = """.+/jquery\.fancytree-.*\.jar""",
      pathInJarPattern = """.+/jquery\.fancytree-all\.min\.js""",
      replacement      = _.replaceAll("""}\(jQuery\b""", "}(ORBEON.jQuery")
    )
  )

  case class Patch(
    jarPathPattern  : String,
    pathInJarPattern: String,
    replacement     : String => String
  ) {
    def matchesJarPath(jarFile: File): Boolean = jarFile.getAbsolutePath.matches(jarPathPattern)
    def matchesPathInJar(pathInJar: String): Boolean = pathInJar.matches(pathInJarPattern)
  }

  val patchWebJars  = taskKey[Seq[File]]("Apply patches to WebJar files")

  // Patched JAR files will be stored in a separate directory
  def patchDir = Def.task((ThisBuild / baseDirectory).value / "src" / "target" / "webjar-patches")

  def coreSettings: Seq[Setting[?]] = Seq(
    patchWebJars                  := patchWebJarsTask.value,
    Compile / compile             := (Compile / compile).dependsOn(patchWebJars).value,
    Compile / dependencyClasspath := classpathWithPatchedJarFiles((Compile / dependencyClasspath).value, patchDir.value)
  )

  def warSettings: Seq[Setting[?]] = Seq(
    Runtime / fullClasspath := classpathWithPatchedJarFiles((Runtime / fullClasspath).value, patchDir.value)
  )

  private def patchWebJarsTask: Def.Initialize[Task[Seq[File]]] = Def.task {

    val updateReport   = update.value // Resolved configurations, modules, and artifacts
    val destinationDir = patchDir.value

    if (patches.nonEmpty) {
      IO.createDirectory(destinationDir)
    }

    for {
      file  <- updateReport.allFiles
      patch <- patches
      if patch.matchesJarPath(file)
    } yield {
      // Apply all patches that apply to this JAR file
      patchJar(file, patch, destinationDir)
    }
  }

  private def patchJar(originalJar: File, patch: Patch, patchDir: File): File = {
    val patchedJar = patchDir / s"patched-${originalJar.getName}"
    val jarInput   = new JarInputStream (new FileInputStream (originalJar))
    val jarOutput  = new JarOutputStream(new FileOutputStream(patchedJar))

    try {

      @tailrec
      def patchEntryIfNeeded(entryOpt: Option[JarEntry]): Unit = entryOpt match {
        case None        => // All entries have been processed
        case Some(entry) =>

          val entryName = entry.getName

          if (patch.matchesPathInJar(entryName)) {
            // Read and patch content
            val originalContent = new String(IO.readBytes(jarInput), "UTF-8")
            val patchedContent  = patch.replacement(originalContent)

            val newEntry = new JarEntry(entryName)
            newEntry.setTime(entry.getTime)

            jarOutput.putNextEntry(newEntry)
            jarOutput.write(patchedContent.getBytes("UTF-8"))
          } else {
            // No patching, copy entry
            jarOutput.putNextEntry(new JarEntry(entry))
            IO.transfer(jarInput, jarOutput)
          }

          jarOutput.closeEntry()
          jarInput.closeEntry()

          // Process next entry
          patchEntryIfNeeded(entryOpt = Option(jarInput.getNextJarEntry))
      }

      patchEntryIfNeeded(entryOpt = Option(jarInput.getNextJarEntry))

    } finally {
      jarInput.close()
      jarOutput.close()
    }

    patchedJar
  }

  private def classpathWithPatchedJarFiles(classpath: Classpath, patchDir: File): Classpath = {

    // Patched JAR files are kept in a separate directory ⇒ remove matching original JAR files from classpath

    val nonPatchedJars = classpath.filterNot(classpathFile => patches.exists(_.matchesJarPath(classpathFile.data)))
    val patchedJars    = (patchDir ** "*.jar").get.map(Attributed.blank)

    nonPatchedJars ++ patchedJars
  }
}