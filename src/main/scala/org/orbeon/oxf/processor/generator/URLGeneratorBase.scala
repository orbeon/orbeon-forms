/**
 * Copyright (C) 2015 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.generator

import java.{lang ⇒ jl, util ⇒ ju}

import org.orbeon.dom.Element
import org.orbeon.oxf.util.{DateUtils, CollectionUtils}
import org.orbeon.oxf.xml.Dom4j

import scala.collection.JavaConverters._

object URLGeneratorBase {

  def extractHeaders(configElement: Element): Map[String, Array[String]] = {

    val headerPairs =
      for {
        headerElem  ← Dom4j.elements(configElement, "header")
        headerName  = headerElem.element("name").getStringValue
        valueElem   ← headerElem.elements("value").asScala
        headerValue = valueElem.getStringValue
      } yield
        headerName → headerValue

    CollectionUtils.combineValues[String, String, Array](headerPairs).toMap
  }

  def setIfModifiedIfNeeded(
    headersOrNull      : Map[String, Array[String]],
    lastModifiedOrNull : jl.Long
  ): ju.Map[String, Array[String]] = {

    val headersOrEmpty  = Option(headersOrNull) getOrElse Map.empty[String, Array[String]]
    val newHeaderAsList = Option(lastModifiedOrNull).map(lastModified ⇒ "If-Modified-Since" → Array(DateUtils.RFC1123Date.print(lastModified))).to[List]

    headersOrEmpty ++ newHeaderAsList
  }.asJava
}
