package org.orbeon.io

import java.net.URI


object FileUtils {
  def isTemporaryFileUri(uri: URI): Boolean = ???
  def findFileUriPath(uri: URI): Option[String] = ???
  def findTemporaryFilePath(uri: URI): Option[String] = ???
}
