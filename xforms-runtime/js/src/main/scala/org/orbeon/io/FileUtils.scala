package org.orbeon.io

import java.net.URI


object FileUtils {
  def isTemporaryFileUri(uri: URI): Boolean = {
    uri.getScheme == "upload" && uri.getSchemeSpecificPart.nonEmpty
  }

  def findFileUriPath(uri: URI): Option[String] = throw new NotImplementedError("findFileUriPath")
  def findTemporaryFilePath(uri: URI): Option[String] = throw new NotImplementedError("findTemporaryFilePath")
}
