import sbt.*


object DemoSqliteDatabaseGenerator extends CachedGenerator {

  private def demoFilesToImport(generatorContext: GeneratorContext): File =
    generatorContext.rootDirectory / "data" / "orbeon" / "fr"

  override val cachePath: String = "sqlite-cache"

  override val runnerProject: ProjectReference = LocalProject("demoSqliteDatabase")

  override def inputFiles(generatorContext: GeneratorContext): Set[File] =
    allFilesIn(demoFilesToImport(generatorContext)).toSet

  override def generate(inputFiles: Set[File])(implicit generatorContext: GeneratorContext): Set[File] = {

    // Running DemoSqliteDatabase here using (demoSqliteDatabase / Compile / runMain).toTask(...).value doesn't work

    val sqliteDatabaseFilename = "orbeon-demo.sqlite"
    val generatedFile          = generatorContext.managedResourcesDirectory / sqliteDatabaseFilename
    val generatedFileInWebInf  = generatorContext.targetDirectory / "webapp" / "WEB-INF" / sqliteDatabaseFilename

    generatorContext.log.info(s"Generating SQLite database at $generatedFile")

    generatorContext.run(
      mainClass = "org.orbeon.oxf.util.DemoSqliteDatabase",
      options   = Seq(demoFilesToImport(generatorContext).toString, generatedFile.toString),
    )

    // Copy .sqlite file from managed resources to target directory
    IO.copy(Set(generatedFile -> generatedFileInWebInf))

    // We need to explicitly copy files from the managed resources directory to the target directory because the
    // standard sbt mechanisms (copyResources and packageBin/mappings - or `package`/mappings) aren't working as expected.

    // Return generated file in managed resources directory
    Set(generatedFile)
  }
}