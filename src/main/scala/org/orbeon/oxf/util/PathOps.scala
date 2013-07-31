/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.util

import java.net.URLEncoder.{encode ⇒ encodeURL}
import java.net.URLDecoder.{decode ⇒ decodeURL}


trait PathOps {

    def dropTrailingSlash(s: String)   = if (s.size == 0 || s.last != '/') s else s.init
    def dropStartingSlash(s: String)   = if (s.size == 0 || s.head != '/') s else s.tail
    def appendStartingSlash(s: String) = if (s.size != 0 && s.head == '/') s else '/' + s

    // Split out a URL's query part
    def splitQuery(url: String): (String, Option[String]) = {
        val index = url.indexOf('?')
        if (index == -1)
            (url, None)
        else
            (url.substring(0, index), Some(url.substring(index + 1)))
    }

    // Recombine a path/query and parameters into a resulting URL
    def recombineQuery(pathQuery: String, params: Seq[(String, String)]) =
        pathQuery + (if (params.isEmpty) "" else (if (pathQuery.contains("?")) "&" else "?") + encodeSimpleQuery(params))

    // Decode a query string into a sequence of pairs
    // We assume that there are no spaces in the input query
    def decodeSimpleQuery(query: String): Seq[(String, String)] =
        for {
            nameValue      ← query.split('&').toList
            if nameValue.nonEmpty
            nameValueArray = nameValue.split('=')
            if nameValueArray.size >= 1
            encodedName    = nameValueArray(0)
            if encodedName.nonEmpty
            decodedName    = decodeURL(encodedName, "utf-8")
            decodedValue   = decodeURL(nameValueArray.lift(1) getOrElse "", "utf-8")
        } yield
            decodedName → decodedValue

    // Get the first query parameter value for the given name
    def getFirstQueryParameter(url: String, name: String) = {
        val query = splitQuery(url)._2 map decodeSimpleQuery getOrElse Seq()

        query find (_._1 == name) map { case (k, v) ⇒ v }
    }

    // Encode a sequence of pairs to a query string
    def encodeSimpleQuery(parameters: Seq[(String, String)]): String =
        parameters map { case (name, value) ⇒ encodeURL(name, "utf-8") + '=' + encodeURL(value, "utf-8") } mkString "&"
}
