import $ivy.`org.xerial:sqlite-jdbc:3.44.1.0`

import java.sql.{Connection, DriverManager, PreparedStatement}
import scala.xml._

@main
def main(path: String): Unit = {
  val buildPath = os.Path(path)

  val sqliteFile        = buildPath / os.RelPath("orbeon-war/jvm/target/webapp/WEB-INF/orbeon-demo.sqlite")
  val sqlScript         = buildPath / os.RelPath("form-runner/jvm/src/main/resources/apps/fr/persistence/relational/ddl/2023.1/sqlite-2023_1.sql")
  val directoryToImport = buildPath / os.RelPath("data/orbeon/fr")

  // Delete any previously existing SQLite file
  os.remove(sqliteFile)

  // Create parent directories if needed
  sqliteFile.toIO.getParentFile.mkdirs()

  val url                    = s"jdbc:sqlite:$sqliteFile"
  val connection: Connection = DriverManager.getConnection(url)

  try {
    createTables(connection, sqlScript)
    importData  (connection, directoryToImport)
  } finally {
    connection.close()
  }
}

def createTables(connection: Connection, sqlScript: os.Path): Unit = {
  val scriptContent = os.read(sqlScript)

  // Split the script into individual statements and execute them

  for {
    rawSqlStatement <- scriptContent.split(";")
    sqlStatement     = rawSqlStatement.trim
    if sqlStatement.nonEmpty
  } {
    val statement = connection.createStatement()

    try {
      statement.execute(sqlStatement)
    } finally {
      statement.close()
    }
  }
}

def importData(connection: Connection, directoryToImport: os.Path): Unit = {
  val FormDefinition           = """(orbeon)/([^\/]+)/form/form.xhtml""".r
  val FormData                 = """(orbeon)/([^\/]+)/data/([^\/]+)/data.xml""".r
  val FormDefinitionAttachment = """(orbeon)/([^\/]+)/form/([^\/]+)""".r
  val FormDataAttachment       = """(orbeon)/([^\/]+)/data/([^\/]+)/([^\/]+)""".r

  os.walk(directoryToImport) filter
    (os.isFile) filterNot
    (_.ext == "DS_Store") filterNot
    (_.baseName.contains("copy")) filterNot
    (_.ext.endsWith("~")) foreach { p =>

    val relativePath     = p.relativeTo(directoryToImport)
    val fileContent      = os.read.bytes(p)
    val currentTimeStamp = new java.sql.Timestamp(System.currentTimeMillis())

    // Version 2 temporarily as on demo we have this and have a /new bug
    val formVersion      = if (p.toString.contains("orbeon/controls")) 2 else 1

    val commonColumns            = "created, last_modified_time, app, form, form_version, deleted"
    val commonColumnsXml         = s"$commonColumns, xml"
    val commonColumnsFileContent = s"$commonColumns, file_content"

    def setCommonColumns(ps: PreparedStatement, app: String, form: String): Unit = {
      ps.setTimestamp(1, currentTimeStamp) // created
      ps.setTimestamp(2, currentTimeStamp) // last_modified_time
      ps.setString   (3, app)              // app
      ps.setString   (4, form)             // form
      ps.setInt      (5, formVersion)      // form_version
      ps.setString   (6, "N")              // deleted
      ps.setBytes    (7, fileContent)      // xml
    }

    relativePath.toString match {
      case FormDefinition(app, form) =>
        val insertQuery =
          s"INSERT INTO main.orbeon_form_definition ($commonColumnsXml, form_metadata) VALUES " +
          "(?, ?, ?, ?, ?, ?, ?, ?)"

        // TODO: extract the metadata correctly (see dataAndMetadataAsString)
        val metadata = metadataFromFormDefinition(fileContent)

        val ps = connection.prepareStatement(insertQuery)
        setCommonColumns(ps, app, form)
        ps.setString(8, metadata) // form_metadata
        ps.executeUpdate()
        ps.close()

      case FormData(app, form, doc) =>
        val insertQuery =
          s"INSERT INTO main.orbeon_form_data ($commonColumnsXml, document_id, draft) VALUES " +
          "(?, ?, ?, ?, ?, ?, ?, ?, ?)"

        val ps = connection.prepareStatement(insertQuery)
        setCommonColumns(ps, app, form)
        ps.setString(8, doc) // document_id
        ps.setString(9, "N") // draft
        ps.executeUpdate()
        ps.close()

      case FormDefinitionAttachment(app, form, filename) =>
        val insertQuery =
          s"INSERT INTO main.orbeon_form_definition_attach ($commonColumnsFileContent, file_name) VALUES " +
          "(?, ?, ?, ?, ?, ?, ?, ?)"

        val ps = connection.prepareStatement(insertQuery)
        setCommonColumns(ps, app, form)
        ps.setString(8, filename) // file_name
        ps.executeUpdate()
        ps.close()

      case FormDataAttachment(app, form, doc, filename) =>
        val insertQuery =
          s"INSERT INTO main.orbeon_form_data_attach ($commonColumnsFileContent, document_id, draft, file_name) VALUES " +
          "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

        val ps = connection.prepareStatement(insertQuery)
        setCommonColumns(ps, app, form)
        ps.setString(8,  doc)       // document_id
        ps.setString(9,  "N")       // draft
        ps.setString(10, filename)  // file_name
        ps.executeUpdate()
        ps.close()

      case _ =>
        println(s"no match for $relativePath")
    }
  }
}

// Ideally, we'd like to call the same code used in RequestReader.dataAndMetadataAsString.
// Assumption: first metadata node in the demo form definitions is the form metadata node (in fr-form-metadata instance)
def metadataFromFormDefinition(formDefinitionAsBytes: Array[Byte]): String = {
  val formDefinition = new String(formDefinitionAsBytes, "UTF-8")
  val xml            = XML.loadString(formDefinition)
  val elementsToKeep = Set("metadata", "title", "permissions", "available") // See RequestReader.MetadataElementsToKeep
  val metadataNode   = filterElements(xml \\ "metadata", elementsToKeep).headOption.getOrElse(sys.error("No metadata node found"))
  new PrettyPrinter(0, 0).format(metadataNode)
}

def filterElements(nodeSeq: NodeSeq, elementsToKeep: Set[String]): NodeSeq =
  nodeSeq.flatMap {
    case elem: Elem =>
      if (elementsToKeep.contains(elem.label)) {
        Some(Elem(
          prefix        = elem.prefix,
          label         = elem.label,
          attributes    = elem.attributes,
          scope         = elem.scope,
          minimizeEmpty = elem.minimizeEmpty,
          child         = filterElements(elem.child, elementsToKeep): _*
        ))
      } else {
        None
      }
    case other =>
      Some(other)
  }
