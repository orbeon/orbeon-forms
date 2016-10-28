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

import org.orbeon.oxf.fr.persistence._
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.Provider.{MySQL, PostgreSQL}
import org.orbeon.oxf.util.{IndentedLogger, Logging}
import org.orbeon.oxf.util.IOUtils._
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.{IndentedLogger, Logging}

private[persistence] object Connect {

  import DataSourceSupport._

  def withOrbeonTables[T]
    (message: String)
    (block: (java.sql.Connection, Provider) ⇒ T)
    (implicit logger: IndentedLogger)
  : Unit = {
    Logging.withDebug(message) {
      ProvidersTestedAutomatically.foreach { provider ⇒
        Logging.withDebug("on database", List("provider" → provider.name)) {
          Connect.withNewDatabase(provider) { connection ⇒
            val statement = connection.createStatement
            // Create tables
            val sql = provider match {
              case MySQL      ⇒ "mysql-2016_3.sql"
              case PostgreSQL ⇒ "postgresql-2016_3.sql"
            }
            val createDDL = SQL.read(sql)
            Logging.withDebug("creating tables") { SQL.executeStatements(provider, statement, createDDL) }
            Logging.withDebug("run actual test") { block(connection, provider) }
          }
        }
      }
    }
  }

  def withNewDatabase[T](provider: Provider)(block: Connection ⇒ T): T = {
    val buildNumber = System.getenv("TRAVIS_BUILD_NUMBER")
    val schema = s"orbeon_$buildNumber"
    val createUserAndDatabase = Seq(s"CREATE DATABASE $schema")
    val dropUserAndDatabase   = Seq(s"DROP DATABASE $schema")
    try {
      Connect.asRoot  (provider)(createUserAndDatabase foreach _.createStatement.executeUpdate)
      Connect.asOrbeon(provider)(block)
    } finally {
      Connect.asRoot(provider)(dropUserAndDatabase foreach _.createStatement.executeUpdate)
    }
  }

  private def asRoot  [T](provider: Provider)(block: Connection ⇒ T): T =
    asUser(provider, None, block)
  private def asOrbeon[T](provider: Provider)(block: Connection ⇒ T): T =
    asUser(provider, Some(orbeonUserWithBuildNumber), block)

  private def asUser[T](provider: Provider, user: Option[String], block: Connection ⇒ T): T = {
    val descriptor = DatasourceDescriptor(provider, user)
    DataSourceSupport.withDatasources(List(descriptor)) {
      val connection = DriverManager.getConnection(descriptor.url, descriptor.username, descriptor.password)
      useAndClose(connection)(block)
    }
  }

  def getTableNames(provider: Provider, connection: Connection): List[String] = {
    val query = provider match {
      case MySQL ⇒
        """SELECT table_name
          |  FROM information_schema.tables
          | WHERE table_name LIKE 'orbeon%'
          |       AND table_schema = DATABASE()"""
      case PostgreSQL ⇒
        """SELECT table_name
          |  FROM information_schema.tables
          | WHERE table_name LIKE 'orbeon%'"""
      case provider ⇒
        throw new IllegalArgumentException(s"unsupported provider `${provider.name}`")
    }
    useAndClose(connection.createStatement.executeQuery(query.stripMargin)) { tableNameResultSet ⇒
      val tableNamesList = Iterator
        .iterateWhile(tableNameResultSet.next(), tableNameResultSet.getString(1))
        .toList
        .sorted
      tableNamesList ensuring (_.nonEmpty)
    }
  }
}
