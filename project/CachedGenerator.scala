import sbt.*
import sbt.Keys.*


case class GeneratorContext(
  rootDirectory            : File,
  baseDirectory            : File,
  sourceDirectory          : File,
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

  def cachePath: String

  def generateOnlyWhenPackaging: Boolean = true

  def runnerProject: ProjectReference = LocalProject("root")

  def inputFiles(generatorContext: GeneratorContext): Set[File]

  def generate(inputFiles: Set[File])(implicit generatorContext: GeneratorContext): Set[File]

  // Return all files in the given directory (recursive)
  def allFilesIn(directory: File): Seq[File] =
    (directory ** "*").filter(_.isFile).get

  def task: Def.Initialize[Task[Seq[File]]] = Def.taskDyn {

    val generatorContext =
      GeneratorContext(
        rootDirectory             = Path.absolute((LocalRootProject / baseDirectory).value),
        baseDirectory             = (Compile / baseDirectory).value,
        sourceDirectory           = (Compile / sourceDirectory).value,
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
        val cachedFunction = FileFunction.cached(generatorContext.cacheDirectory / cachePath)(generate(_)(generatorContext))

        // Generate the files only if they're missing or if any of the input files has changed
        cachedFunction(inputFiles(generatorContext)).toSeq
      } else {
        Seq.empty[File]
      }
    }
  }
}