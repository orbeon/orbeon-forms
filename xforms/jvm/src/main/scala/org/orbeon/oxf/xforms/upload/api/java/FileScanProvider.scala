/**
  * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.upload.api.java

import _root_.java.{util => ju}

import org.orbeon.oxf.xforms.upload.api
import org.orbeon.oxf.xforms.upload.api.java

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.util.Try
import scala.collection.compat._

abstract class FileScanProvider extends api.FileScanProvider {

  def startStream(fileName: String, headers: ju.Map[String, Array[String]]): java.FileScan

  private class FileScanWrapper(fc: java.FileScan) extends api.FileScan {

    def bytesReceived(byte: Array[Byte], offset: Int, length: Int): api.FileScanStatus =
      api.FileScanStatus.values(fc.bytesReceived(byte, offset, length).ordinal)

    def complete(file: _root_.java.io.File): api.FileScanStatus =
      api.FileScanStatus.values(fc.complete(file).ordinal)

    def abort(): Unit =
      fc.abort()
  }

  def startStream(fileName: String, headers: Seq[(String, Seq[String])]): Try[api.FileScan] =
    Try(
      startStream(
        fileName,
        mutable.LinkedHashMap(headers map { case (k, v) => k -> v.to(Array) }: _*).asJava
      )
    ) map (new FileScanWrapper(_))
}
