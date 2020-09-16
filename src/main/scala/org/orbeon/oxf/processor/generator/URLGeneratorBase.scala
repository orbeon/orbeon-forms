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

import java.{lang => jl, util => ju}

import org.orbeon.dom.Element
import org.orbeon.oxf.util.{CollectionUtils, ConnectionResult, DateUtils, NetUtils}
import org.orbeon.oxf.xml.dom.Comparator

import scala.collection.JavaConverters._
import scala.collection.compat._

object URLGeneratorBase {

  def extractHeaders(configElement: Element): Map[String, List[String]] = {

    val headerPairs =
      for {
        headerElem  <- configElement.elements("header")
        headerName  = headerElem.element("name").getStringValue
        valueElem   <- headerElem.elements("value")
        headerValue = valueElem.getStringValue
      } yield
        headerName -> headerValue

    CollectionUtils.combineValues[String, String, List](headerPairs).toMap
  }

  def headersToString(headersOrNull: Map[String, List[String]]): String = {
    val headers = Option(headersOrNull) getOrElse Map.empty
    headers map (_.toString) mkString ("Map(", ",", ")")
  }

  def setIfModifiedIfNeeded(
    headersOrNull      : Map[String, List[String]],
    lastModifiedOrNull : jl.Long
  ): ju.Map[String, Array[String]] = {

    val headersOrEmpty  = Option(headersOrNull) map { _ map { case (k, v) => (k, v.to(Array)) }} getOrElse Map.empty[String, Array[String]]
    val newHeaderAsList = Option(lastModifiedOrNull).map(lastModified => "If-Modified-Since" -> Array(DateUtils.formatRfc1123DateTimeGmt(lastModified))).to(List)

    headersOrEmpty ++ newHeaderAsList
  }.asJava

  // Save headers as request attributes
  def collectHeaders(connectionResult: ConnectionResult, readHeader: ju.List[String]): List[(String, String)] =
    if ((readHeader ne null) && ! readHeader.isEmpty) {
      for {
        nameMaybeMixed <- readHeader.asScala.to(List)
        list           = connectionResult.getHeaderIgnoreCase(nameMaybeMixed)
        if list.nonEmpty // only support headers with one value
        value          = list.head
        nameLower      = nameMaybeMixed.toLowerCase
      } yield {
        nameMaybeMixed -> value
      }
    } else {
      Nil
    }

  def storeHeadersIntoRequest(connectionResult: ConnectionResult, headers: List[(String, String)]): Unit = {
    val requestAttributes = NetUtils.getExternalContext.getRequest.getAttributesMap
    headers foreach { case (name, value) =>
      requestAttributes.put("oxf.url-generator.header." + name.toLowerCase, value)
    }
  }
}
