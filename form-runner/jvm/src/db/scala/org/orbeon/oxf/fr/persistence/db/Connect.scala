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

import org.orbeon.io.IOUtils.*
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.Provider.*
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.{IndentedLogger, Logging}

import java.sql.{Connection, DriverManager}
import scala.sys.process.*
import scala.util.Try


object Connect {

  val TestDatabaseName = "orbeon_unit_tests"

  lazy val ProvidersTestedAutomatically: List[Provider] = List(
    Provider.withName(sys.env("DB"))
  )

  def createTables(provider: Provider, connection: java.sql.Connection)(implicit logger: IndentedLogger): Unit = {
    val statement = connection.createStatement
    val sql = provider match {
      case MySQL      => "2025.1/mysql-2025_1.sql"
      case PostgreSQL => "2025.1/postgresql-2025_1.sql"
      case SQLite     => "2025.1/sqlite-2025_1.sql"
    }
    val createDDL = SQL.read(sql)
    Logging.withDebug("creating tables") { SQL.executeStatements(provider, statement, createDDL) }
  }

  def withOrbeonTables[T]
    (message: String)
    (block: (java.sql.Connection, Provider) => T)
    (implicit logger: IndentedLogger)
  : Unit = {
    Logging.withDebug(message) {
      ProvidersTestedAutomatically.foreach { provider =>
        Logging.withDebug("on database", List("provider" -> provider.entryName)) {
          Connect.withNewDatabase(provider) { connection =>
            createTables(provider, connection)
            Logging.withDebug("run actual test") { block(connection, provider) }
          }
        }
      }
    }
  }

  def withNewDatabase[T]
    (provider: Provider)
    (block: Connection => T)
    (implicit logger: IndentedLogger)
  : T = withNewDatabase(provider, DatasourceDescriptor(provider), dropDatabase = true)(block)

  def withNewDatabase[T]
    (provider: Provider, datasourceDescriptor: DatasourceDescriptor, dropDatabase: Boolean)
    (block: Connection => T)
    (implicit logger: IndentedLogger)
  : T = {

    def logRunDocker[U](body: => U): U =
      Logging.withDebug("run Docker and wait for connection")(body)

    try {

      withConnection(datasourceDescriptor) { connection =>

        val createUser: PartialFunction[Provider, Unit] = {
          case MySQL      => runStatements(connection, List(s"CREATE DATABASE $TestDatabaseName"))
          case PostgreSQL => runStatements(connection, List(s"CREATE SCHEMA   $TestDatabaseName"))
          case SQLite     =>
        }

        if (createUser.isDefinedAt(provider)) {
          Logging.withDebug(s"create `$TestDatabaseName` schema") {
            createUser(provider)
          }
        }
        // Run block
        datasourceDescriptor.switchDB.foreach(switchDB => runStatements(connection, List(switchDB)))
        block(connection)
      }

    } finally {
      if (dropDatabase) {
        Logging.withDebug(s"drop `$TestDatabaseName` objects") {
          provider match {
            case MySQL        => runStatements(datasourceDescriptor, List(s"DROP DATABASE  $TestDatabaseName"))
            case PostgreSQL   => runStatements(datasourceDescriptor, List(s"DROP SCHEMA    $TestDatabaseName    CASCADE"))
            case SQLite       => withConnection(datasourceDescriptor) { connection =>
                                    datasourceDescriptor.switchDB.foreach(switchDB => runStatements(connection, List(switchDB)))
                                    val tables = getTableNames(provider, connection)
                                    val dropStatements = tables.map("DROP TABLE IF EXISTS " + _)
                                    runStatements(connection, dropStatements)
                                   }
          }
        }
      }
    }
  }

  // TODO: No callers?
  def rmContainer(image: String): Unit = {
    val db2ContainerId = s"docker ps -q --filter ancestor=$image".!!
    s"docker rm -f $db2ContainerId".!!
  }

  private def waitUntilCanConnect(datasourceDescriptor: DatasourceDescriptor): Unit = {
    def tryConnecting() = Try(withConnection(datasourceDescriptor)(_ => ()))
    while (tryConnecting().isFailure) Thread.sleep(100)
  }

  private def runStatements(
    datasourceDescriptor: DatasourceDescriptor,
    statements: List[String])
  : Unit = {
    withConnection(datasourceDescriptor) { connection =>
      statements.foreach(connection.createStatement.executeUpdate)
    }
  }

  private def runStatements(
    connection: Connection,
    statements: List[String])
  : Unit = {
    val jdbcStatement = connection.createStatement()
    statements.foreach(jdbcStatement.executeUpdate)
  }

  private def withConnection[T](
    datasourceDescriptor : DatasourceDescriptor)(
    block                : Connection => T)
  : T =
  {
    DataSourceSupport.withDatasources(List(datasourceDescriptor)) {
      val connection = DriverManager.getConnection(
        datasourceDescriptor.url,
        datasourceDescriptor.username,
        datasourceDescriptor.password
      )
      useAndClose(connection)(block)
    }
  }

  // For `TABLESPACE` and `BUFFERPOOL`, Db2 does not support `IF EXISTS` in the `DROP` statement
  private def db2DropIfExists(
    connection    : Connection,
    dropStatement : String
  ): Unit = {
    try {
      runStatements(connection, List(dropStatement))
    } catch {
      case e: java.sql.SQLException if e.getSQLState == "42704" => // NOP
      case e: Exception                                         => throw e
    }
  }

  def getTableNames(provider: Provider, connection: Connection): List[String] = {
    val query = provider match {
      case MySQL =>
        """SELECT table_name
          |  FROM information_schema.tables
          | WHERE table_name LIKE 'orbeon%'
          |       AND table_schema = DATABASE()"""
      case PostgreSQL =>
        """SELECT table_name
          |  FROM information_schema.tables
          | WHERE table_name LIKE 'orbeon%'"""
      case SQLite =>
        """SELECT name FROM sqlite_schema
          |WHERE type = 'table'
          |AND name NOT LIKE 'sqlite_%';"""
    }
    useAndClose(connection.createStatement.executeQuery(query.stripMargin)) { tableNameResultSet =>
      val tableNamesList = Iterator
        .iterateWhile(tableNameResultSet.next(), tableNameResultSet.getString(1))
        .toList
        .sorted
      tableNamesList ensuring (_.nonEmpty)
    }
  }
}
