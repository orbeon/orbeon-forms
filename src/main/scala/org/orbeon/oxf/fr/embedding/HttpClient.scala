/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.fr.embedding

import java.io.{InputStream, OutputStream}

import scala.collection.immutable

trait HttpResponse {
    def statusCode : Int
    def headers    : immutable.Map[String, String]
    def inputStream: InputStream
    def contentType: String
}

trait HttpClient {
    def openConnection(
        url         : String,
        content     : Option[(Option[String], OutputStream ⇒ Unit)],
        headers     : immutable.Iterable[(String, String)])(
        implicit ctx: EmbeddingContext
    ): HttpResponse
}
