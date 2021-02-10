package org.orbeon.io

import java.net.URI


object FileUtils {
  def isTemporaryFileUri(uri: URI): Boolean = throw new NotImplementedError("isTemporaryFileUri")
  def findFileUriPath(uri: URI): Option[String] = throw new NotImplementedError("findFileUriPath")
  def findTemporaryFilePath(uri: URI): Option[String] = throw new NotImplementedError("findTemporaryFilePath")
}
