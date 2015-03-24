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

import java.{util ⇒ ju}

import org.dom4j.Element
import org.orbeon.oxf.util.{DateUtils, ScalaUtils}

import scala.collection.JavaConverters._

object URLGeneratorBase {

    def extractHeaders(configElement: Element): Map[String, Array[String]] = {

        val headerPairs =
            for {
                headerElem  ← configElement.selectNodes("/config/header").asInstanceOf[ju.List[Element]].asScala
                headerName  = headerElem.element("name").getStringValue
                valueElem   ← headerElem.elements("value").asInstanceOf[ju.List[Element]].asScala
                headerValue = valueElem.getStringValue
            } yield
                headerName → headerValue

        ScalaUtils.combineValues[String, String, Array](headerPairs).toMap
    }

    def setIfModifiedIfNeeded(headers: Map[String, Array[String]], lastModified: java.lang.Long) = {
        if (lastModified ne null) {
            
            val newHeader = "If-Modified-Since" → Array(DateUtils.RFC1123Date.print(lastModified))
            
            if (headers ne null)
                headers + newHeader
            else
                Map(newHeader)
        } else {
            headers
        }
    }.asJava
}
