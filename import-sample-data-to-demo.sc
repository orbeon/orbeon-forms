// Run with: `amm import-sample-data-to-demo.sc username password`
// https://ammonite.io/#AmmoniteArgumentsinScripts

@main
def main(username: String, password: String): Unit = {

  val root         = os.pwd / os.RelPath("data/orbeon/fr")
  val crudEndPoint = "http://localhost:8080/orbeon/fr/service/persistence/crud/"

  os.walk(root)                   filter
    (os.isFile)                   filterNot
    (_.ext == "DS_Store")         filterNot
    (_.baseName.contains("copy")) filterNot
    (_.ext.endsWith("~"))         foreach { p =>

    println(s"processing file: ${p.relativeTo(root)}")

    val mediatype = p.ext match {
      case "jpg"           => "image/jpeg"
      case "xml" | "xhtml" => "application/xml"
      case _               => "application/octet-stream"
    }

    // Version 2 temporarily as on demo we have this and have a /new bug
    val formVersion = if (p.toString.contains("orbeon/controls")) 2 else 1

    val r =
      requests.put(
        crudEndPoint + p.relativeTo(root),
        auth    = new requests.RequestAuth.Basic(username, password),
        data    = new java.io.File(p.toString),
        headers = Map(
          "Content-Type"                   -> mediatype,
          "Orbeon-Form-Definition-Version" -> formVersion.toString
        )
      )

    println(s"${r.statusCode}")
  }
}