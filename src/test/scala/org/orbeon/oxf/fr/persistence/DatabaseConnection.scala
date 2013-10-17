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
package org.orbeon.oxf.fr.persistence

import java.sql.{DriverManager, Connection}
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.resources.URLFactory
import java.io.{StringWriter, InputStreamReader}
import scala.collection.mutable.ListBuffer

trait DatabaseConnection {

    val Base = "oxf:/apps/fr/persistence/relational/ddl"

    def asRoot  [T](block: Connection ⇒ T): T = asUser(user = "root"         , password = None                 , database = None,                  block)
    def asOrbeon[T](block: Connection ⇒ T): T = asUser(user = "orbeon_ddl"   , password = Some("orbeon_ddl")   , database = Some("orbeon_ddl"),    block)
    def asTomcat[T](block: Connection ⇒ T): T = asUser(user = "orbeon_tomcat", password = Some("orbeon_tomcat"), database = Some("orbeon_tomcat"), block)

    private def asUser[T](user: String, password: Option[String], database: Option[String], block: Connection ⇒ T): T = {
        val databaseString = database getOrElse ""
        val url = s"jdbc:mysql://localhost/$databaseString"
        useAndClose(DriverManager.getConnection(url, user, password.getOrElse("")))(block)
    }

    // Reads a sequence semicolon-separated of statements from a text file
    def readSQL(url: String): Seq[String] = {
        val inputStream = URLFactory.createURL(url).openStream()
        val reader = new InputStreamReader(inputStream)
        val writer = new StringWriter
        copyReader(reader, writer)
        val sql = writer.toString.split(";")
        sql map (_.trim) filter (_.nonEmpty)
    }

    def getTableNames(connection: Connection): Seq[String] = {
        val statement = connection.createStatement
        val tableNames = ListBuffer[String]()
        val tablesResultSet = statement.executeQuery("show tables")
        while (tablesResultSet.next()) tableNames += tablesResultSet.getString(1)
        tableNames
    }
}
