import sbt.*

import scala.sys.process.Process


// Generators for the Angular/React JS files in the orbeon-embedding-war project

trait EmbeddingGenerator extends CachedGenerator {

  def framework: String
  def outputFilenames: Set[String]

  override lazy val cachePath: String = s"$framework-cache"

  override def inputFiles(generatorContext: GeneratorContext): Set[File] =
    IO.listFiles(generatorContext.sourceDirectory / framework).filterNot { f =>
      Set(".angular", "dist", "node_modules", "package-lock.json").contains(f.getName)
    }.flatMap { f =>
      if (f.isDirectory) allFilesIn(f) else Set(f)
    }.toSet

  override def generate(inputFiles: Set[File])(implicit generatorContext: GeneratorContext): Set[File] = {

    val scriptPath = generatorContext.sourceDirectory / framework / "build.sh"
    val outputPath = generatorContext.managedResourcesDirectory / "assets" / framework
    val exitCode   = Process(
      command = Seq(scriptPath.getAbsolutePath, outputPath.getAbsolutePath),
      cwd     = scriptPath.getParentFile
    ).!

    if (exitCode != 0) {
      throw new Exception(s"${framework.capitalize} build script failed with exit code: $exitCode")
    }

    val filesToCopy = outputFilenames.map { filename =>
      outputPath / filename -> generatorContext.targetDirectory / "webapp" / "assets" / framework / filename
    }

    // Copy JS files from managed resources to target directory
    IO.copy(filesToCopy)

    // We need to explicitly copy files from the managed resources directory to the target directory because the
    // standard sbt mechanisms (copyResources and packageBin/mappings - or `package`/mappings) aren't working as expected.

    // Return generated files in managed resources directory
    filesToCopy.map(_._1)
  }
}

object EmbeddingAngularGenerator extends EmbeddingGenerator {
  override val framework: String            = "angular"
  override val outputFilenames: Set[String] = Set("main.js", "polyfills.js")
}

object EmbeddingReactGenerator extends EmbeddingGenerator {
  override val framework: String            = "react"
  override val outputFilenames: Set[String] = Set("main.js")
}
