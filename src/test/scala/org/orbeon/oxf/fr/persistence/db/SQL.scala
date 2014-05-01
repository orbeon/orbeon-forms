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
package org.orbeon.oxf.fr.persistence.db

import org.orbeon.oxf.resources.URLFactory
import java.io.{StringWriter, InputStreamReader}
import org.orbeon.oxf.util.ScalaUtils._

private[persistence] object SQL {

    private val Base = "oxf:/apps/fr/persistence/relational/ddl/"

    // Reads a sequence semicolon-separated of statements from a text file
    def read(file: String): Seq[String] = {
        val url = Base ++ file
        val inputStream = URLFactory.createURL(url).openStream()
        val reader = new InputStreamReader(inputStream)
        val writer = new StringWriter
        copyReader(reader, writer)
        val sql = writer.toString.split(";")
        sql map (_.trim) filter (_.nonEmpty)
    }
}
