package org.orbeon.oxf.util

import org.orbeon.sjsdom
import org.scalajs.dom

import java.net.URI
import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array


object JsFileSupport {

  import Private._

  val UploadUriScheme = "upload"

  def createTemporaryFileUri(file: dom.raw.File, size: Long, mediatype: Option[String]): URI = {
    val uri = new URI(UploadUriScheme, java.util.UUID.randomUUID.toString, null)
    temporaryFiles += uri -> FileDetails(file, size, mediatype)
    uri
  }

  def readTemporaryFile(uri: URI): Option[sjsdom.ReadableStream[Uint8Array]] =
    getFileDetails(uri).map { case FileDetails(file, _, _) =>
      file.asInstanceOf[js.Dynamic].stream().asInstanceOf[sjsdom.ReadableStream[Uint8Array]]
    }

  // This can be called by `proxyURI()` multiple times
  def findObjectUrl(uri: URI): Option[String] =
    getFileDetails(uri).map(_.objectUrl)

  def mapSavedUri(
    beforeUri: URI,
    afterUri : URI
  ): Unit =
    getFileDetails(beforeUri).foreach { details =>
      // Keep the existing mapping in case someone tries to read the `upload:` URI again, but add the new mapping
      temporaryFiles += removeQueryAndFragment(afterUri) -> details
    }

  // TODO: Lifecycle of the object URL. It is associated with the document, but can we/should we clean it up before?
  def removeTemporaryFile(uri: URI): Unit = {
    getFileDetails(uri).foreach(_.revokeObjectUrl())
    temporaryFiles -= removeQueryAndFragment(uri)
  }

  private object Private {

    case class FileDetails(file: dom.raw.File, size: Long, mediatype: Option[String]) {

      // The URL must be associated only once, as each call to `URL.createObjectURL()` creates a new URL.
      private var mustRevokeObjectUrl: Boolean = false
      lazy val objectUrl: String = {
        val result = js.Dynamic.global.window.URL.createObjectURL(file).asInstanceOf[String]
        mustRevokeObjectUrl = true
        result
      }

      def revokeObjectUrl(): Unit =
        if (mustRevokeObjectUrl) {
          js.Dynamic.global.window.URL.revokeObjectURL(objectUrl)
          mustRevokeObjectUrl = false
        }
    }

    var temporaryFiles: Map[URI, FileDetails] = Map.empty

    def getFileDetails(uri: URI): Option[FileDetails] =
      temporaryFiles.get(removeQueryAndFragment(uri))

    def removeQueryAndFragment(uri: URI): URI =
      if (uri.isOpaque)
        new URI(uri.getScheme, PathUtils.splitQuery(uri.getSchemeSpecificPart)._1, null)
      else
        new URI(uri.getScheme, uri.getAuthority, uri.getPath, null)
  }
}
