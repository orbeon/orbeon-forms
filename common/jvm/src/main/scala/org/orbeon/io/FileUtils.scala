/**
  * Copyright (C) 2018 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.io

import java.io.File
import java.net.URI
import org.orbeon.oxf.util.CoreUtils._

object FileUtils {

  def isTemporaryFileUri(uri: URI): Boolean =
    findFileUriPath(uri) exists { uriPath =>
      val tmpPath = new File(System.getProperty("java.io.tmpdir")).getCanonicalPath
      new File(uriPath).getCanonicalPath.startsWith(tmpPath)
    }

  def findFileUriPath(uri: URI): Option[String] =
    uri.getScheme == "file" option uri.normalize.getPath

  def findTemporaryFilePath(uri: URI): Option[String] =
    isTemporaryFileUri(uri) flatOption findFileUriPath(uri)
}
