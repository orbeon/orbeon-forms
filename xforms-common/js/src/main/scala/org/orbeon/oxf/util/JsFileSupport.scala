package org.orbeon.oxf.util

import org.orbeon.sjsdom
import org.scalajs.dom

import java.net.URI
import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array


object JsFileSupport {

  val UploadUriScheme = "upload"

  private var temporaryFiles: Map[String, dom.raw.File] = Map.empty

  def createTemporaryFile(file: dom.raw.File, size: Long, mediatype: Option[String]): String = {
    val uuid = java.util.UUID.randomUUID.toString
    temporaryFiles += uuid -> file
    uuid
  }

  def createTemporaryFileUri(file: dom.raw.File, size: Long, mediatype: Option[String]): URI =
    URI.create(s"$UploadUriScheme:${createTemporaryFile(file, size, mediatype)}")

  def readTemporaryFile(uri: URI): Option[sjsdom.ReadableStream[Uint8Array]] =
    temporaryFiles.get(PathUtils.splitQuery(uri.getSchemeSpecificPart)._1) map { file =>
      file.asInstanceOf[js.Dynamic].stream().asInstanceOf[sjsdom.ReadableStream[Uint8Array]]
    }
}
