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
package org.orbeon.oxf.xforms.upload.api

import _root_.java.{util => ju}
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.Try


// Scala API
trait FileScanProvider {

  def init(): Unit
  def destroy(): Unit

  def startStream(filename: String, headers: Seq[(String, Seq[String])]): Try[FileScan]
}

object FileScanProvider {
  def convertHeadersToJava(headers: Seq[(String, Seq[String])]): ju.Map[String, Array[String]] =
    mutable.LinkedHashMap(headers map { case (k, v) => k -> v.toArray }: _*).asJava
}