package org.orbeon.xforms

import java.net.URI


case class ManifestEntry(uri: String, zipPath: String, contentType: Option[String])

object ManifestEntry {

  val JsonFilename = "manifest.json"

  def apply(uri: URI, contentType: Option[String]): ManifestEntry = {
    val normalized = uri.normalize
    val prefix = Option(normalized.getScheme) map ("_" +) getOrElse "_"
    ManifestEntry(normalized.toString, prefix + normalized.getPath, contentType)
  }
}