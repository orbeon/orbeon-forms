import sbt.*
import sbt.Keys.*


case class GeneratorContext(
  inputFilesDirectory      : File,
  baseDirectory            : File,
  cacheDirectory           : File,
  managedResourcesDirectory: File,
  targetDirectory          : File,
  runner                   : ScalaRun,
  dependencyClasspath      : Classpath,
  log                      : Logger
) {

  def run(mainClass: String, options: Seq[String]): Unit =
    runner.run(mainClass, dependencyClasspath.files, options, log).get
}

trait CachedGenerator {

  def inputPath: String
  def cachePath: String

  def generateOnlyWhenPackaging: Boolean = true

  def runnerProject: ProjectReference = LocalProject("root")

  def generate(inputFiles: Set[File])(implicit generatorContext: GeneratorContext): Set[File]

  // Return all files in the given directory (recursive)
  def allFilesIn(directory: File): Seq[File] = {
    val files = directory.listFiles
    files.filter(_.isFile) ++ files.filter(_.isDirectory).flatMap(allFilesIn)
  }

  def task: Def.Initialize[Task[Seq[File]]] = Def.taskDyn {

    val generatorContext =
      GeneratorContext(
        inputFilesDirectory       = Path.absolute((LocalRootProject / baseDirectory).value) / inputPath,
        baseDirectory             = (Compile / baseDirectory).value,
        cacheDirectory            = streams.value.cacheDirectory,
        managedResourcesDirectory = (Compile / resourceManaged).value,
        targetDirectory           = (Compile / target).value,
        runner                    = (runnerProject / Runtime / runner).value,
        dependencyClasspath       = (runnerProject / Runtime / dependencyClasspath).value,
        log                       = streams.value.log
      )

    Def.task {
      // There must be a cleaner, more idiomatic way to do this
      if (! generateOnlyWhenPackaging || state.value.currentCommand.exists(_.commandLine.startsWith("package"))) {
        // Generate the files only if they're missing or if any of the input files has changed
        val inputFiles     = allFilesIn(generatorContext.inputFilesDirectory).toSet
        val cachedFunction = FileFunction.cached(generatorContext.cacheDirectory / cachePath)(generate(_)(generatorContext))

        cachedFunction(inputFiles).toSeq
      } else {
        Seq.empty[File]
      }
    }
  }
}