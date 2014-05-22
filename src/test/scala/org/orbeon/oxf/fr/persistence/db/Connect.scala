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

import java.sql.{DriverManager, Connection}
import org.orbeon.oxf.util.ScalaUtils._

private[persistence] object Connect {

    def asRoot  [T](provider: Provider)(block: Connection ⇒ T): T = asUser(provider, None                 , block)
    def asDDL   [T](provider: Provider)(block: Connection ⇒ T): T = asUser(provider, Some("orbeon_ddl")   , block)
    def asTomcat[T](provider: Provider)(block: Connection ⇒ T): T = asUser(provider, Some("orbeon_tomcat"), block)

    private def asUser[T](provider: Provider, user: Option[String], block: Connection ⇒ T): T = {
        val url = provider match {
            case Oracle    ⇒ System.getenv("ORACLE_URL")
            case MySQL     ⇒ System.getenv("MYSQL_URL")     + user.getOrElse("")
            case SQLServer ⇒ System.getenv("SQLSERVER_URL") + user.map(";databaseName=" + _).getOrElse("")
            case DB2       ⇒ System.getenv("DB2_URL")
        }
        val userName = provider match {
            case Oracle    ⇒ user.getOrElse("orbeon")
            case MySQL     ⇒ user.getOrElse("root") // TODO: As part of the DB setup, we could create an orbeon user for MySQL, to avoid this distinction here
            case SQLServer ⇒ "orbeon"
            case DB2       ⇒ ???
        }
        val password = provider match {
            case MySQL     ⇒ user.getOrElse("")
            case _         ⇒ System.getenv("RDS_PASSWORD")
        }
        useAndClose(DriverManager.getConnection(url, userName, password))(block)
    }

    def getTableNames(provider: Provider, connection: Connection): List[String] = {
        val query = provider match {
            case Oracle ⇒
                """SELECT table_name
                  |  FROM all_tables
                  | WHERE table_name LIKE 'ORBEON%'"""
            case _ ⇒
                """SELECT table_name
                  |  FROM information_schema.tables
                  | WHERE table_name LIKE 'orbeon%'"""
        }
        val tableNameResultSet = connection.createStatement.executeQuery(query.stripMargin)
        val tableNamesList = Iterator.iterateWhile(tableNameResultSet.next(), tableNameResultSet.getString(1)).toList
        tableNamesList ensuring (_.length > 0)
    }
}
