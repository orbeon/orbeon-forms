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

import java.sql.{Connection, DriverManager}

import org.orbeon.oxf.fr.DataSourceSupport
import org.orbeon.oxf.fr.persistence.relational.Provider._
import org.orbeon.oxf.util.ScalaUtils._

private[persistence] object Connect {

  import DataSourceSupport._

  def asRoot  [T](provider: Provider)(block: Connection ⇒ T): T = asUser(provider, None                           , block)
  def asDDL   [T](provider: Provider)(block: Connection ⇒ T): T = asUser(provider, Some(ddlUserFromBuildNumber)   , block)
  def asTomcat[T](provider: Provider)(block: Connection ⇒ T): T = asUser(provider, Some(tomcatUserFromBuildNumber), block)

  private def asUser[T](provider: Provider, user: Option[String], block: Connection ⇒ T): T = {
    val (url, username, password) = connectionDetailsFromEnv(provider, user)
    useAndClose(DriverManager.getConnection(url, username, password))(block)
  }

  def getTableNames(provider: Provider, connection: Connection): List[String] = {
    val query = provider match {
      case Oracle ⇒
        """SELECT table_name
          |  FROM all_tables
          | WHERE table_name LIKE 'ORBEON%'
          |       AND owner = sys_context('USERENV', 'CURRENT_USER')"""
      case MySQL ⇒
        """SELECT table_name
          |  FROM information_schema.tables
          | WHERE table_name LIKE 'orbeon%'
          |       AND table_schema = DATABASE()"""
      case SQLServer | PostgreSQL ⇒
        """SELECT table_name
          |  FROM information_schema.tables
          | WHERE table_name LIKE 'orbeon%'"""
      case provider ⇒
        throw new IllegalArgumentException(s"unsupported provider `${provider.name}`")
    }
    val tableNameResultSet = connection.createStatement.executeQuery(query.stripMargin)
    val tableNamesList = Iterator.iterateWhile(tableNameResultSet.next(), tableNameResultSet.getString(1)).toList
    tableNamesList ensuring (_.nonEmpty)
  }
}
