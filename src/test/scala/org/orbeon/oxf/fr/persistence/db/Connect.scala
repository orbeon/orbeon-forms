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

import java.io.StringWriter
import java.sql.{DriverManager, Connection}
import org.orbeon.oxf.util.ScalaUtils._
import scala.collection.mutable.ListBuffer

private[persistence] object Connect {

    def asRoot  [T](block: Connection ⇒ T): T = asUser(None                 , block)
    def asDDL   [T](block: Connection ⇒ T): T = asUser(Some("orbeon_ddl")   , block)
    def asTomcat[T](block: Connection ⇒ T): T = asUser(Some("orbeon_tomcat"), block)

    private def asUser[T](user: Option[String], block: Connection ⇒ T): T = {
        val url = Config.provider match {
            case MySQL     ⇒ Config.jdbcURL ++ user.getOrElse("")
            case SQLServer ⇒ Config.jdbcURL ++ user.map(";databaseName=" ++ _).getOrElse("")
            case _         ⇒ ???
        }
        val userName = Config.provider match {
            case MySQL     ⇒ user.getOrElse("root")
            case SQLServer ⇒ "orbeon"
            case _         ⇒ ???
        }
        val password = Config.provider match {
            case MySQL     ⇒ user.getOrElse("")
            case SQLServer ⇒ "password"
            case _         ⇒ ???
        }
        useAndClose(DriverManager.getConnection(url, userName, password))(block)
    }

    def getTableNames(connection: Connection): List[String] = {
        val tablesResultSet = connection.createStatement.executeQuery("show tables")
        val tableNames = ListBuffer[String]()
        while (tablesResultSet.next())
            tableNames += tablesResultSet.getString(1)
        println(tableNames.mkString(", "))
        tableNames.toList

        //val x = Stream.continually(tablesResultSet.next() option tablesResultSet.getString(1)).takeWhile(_.isDefined).flatten

        // Boolean Value List[Value]
    }
}
