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
package org.orbeon.oxf.fr.persistence.ddl

import java.sql.Connection
import org.junit.Test
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.oxf.fr.persistence.db._
import org.orbeon.oxf.util.ScalaUtils._

/**
 * Test the DDL we provide to create and update databases.
 */
class DDLTest extends ResourceManagerTestBase with AssertionsForJUnit {

    private def withNewDatabase[T](block: Connection ⇒ T): T = {
        val createUserAndDatabase = Config.provider match {
            case MySQL     ⇒ Seq("create user orbeon_ddl@localhost identified by 'orbeon_ddl'",
                                  "create database orbeon_ddl",
                                  "grant all privileges on orbeon_ddl.* to orbeon_ddl@localhost")
            case SQLServer ⇒ Seq("CREATE DATABASE orbeon_ddl")
            case _         ⇒ ???
        }
        val dropUserAndDatabase = Config.provider match {
            case MySQL     ⇒ Seq("drop user orbeon_ddl@localhost",
                                  "drop database orbeon_ddl")
            case SQLServer ⇒ Seq("DROP DATABASE orbeon_ddl")
            case _         ⇒ ???
        }
        try {
            Connect.asRoot(createUserAndDatabase foreach _.createStatement.executeUpdate)
            Connect.asDDL(block)
        } finally {
            Connect.asRoot(dropUserAndDatabase foreach _.createStatement.executeUpdate)
        }
    }

    case class TableMeta(tableName: String, colsMeta: Seq[ColMeta])
    case class ColMeta(colName: String, meta: Set[ColKeyVal])
    case class ColKeyVal(key: String, value: AnyRef)

    /**
     * Runs the SQL, and returns the information about the tables as defined in the database. The form in which this
     * information is returned varies depending on the database, hence the Any return type.
     */
    private def sqlToTableInfo(sql: Seq[String]): Set[TableMeta] = {
        withNewDatabase { connection ⇒
            val statement = connection.createStatement
            sql foreach statement.executeUpdate
            Connect.getTableNames(connection).map { tableName ⇒
                Config.provider match {
                    case MySQL | SQLServer ⇒
                        val tableInfoResultSet = {
                            val ps = connection.prepareStatement(
                                """SELECT   *
                                  |FROM     information_schema.columns
                                  |WHERE    table_name = ?
                                  |ORDER BY ordinal_position
                                  |""".stripMargin)
                            ps.setString(1, tableName)
                            ps.executeQuery()
                        }
                        def tableInfo(): ColMeta = {
                            val colName = tableInfoResultSet.getString("column_name")
                            val interestingKeys = Set("is_nullable", "data_type")
                            val colKeyVals = for (metaKey ← interestingKeys) yield
                                ColKeyVal(metaKey, tableInfoResultSet.getObject(metaKey))
                            ColMeta(colName, colKeyVals)
                        }
                        val colsMeta = Iterator.iterateWhile(tableInfoResultSet.next(), tableInfo()).toList
                        assert(colsMeta.length > 0)
                        TableMeta(tableName, colsMeta)

                    case _ ⇒ ???
                }
            }.toSet
        }
    }

    @Test def createAndUpgradeTest(): Unit = {
        Config.provider match {
            case MySQL ⇒
                val upgradeTo4_4 = sqlToTableInfo(SQL.read("mysql-4_3.sql") ++ SQL.read("mysql-4_3-to-4_4.sql"))
                val  straight4_4 = sqlToTableInfo(SQL.read("mysql-4_4.sql"))
                val upgradeTo4_5 = sqlToTableInfo(SQL.read("mysql-4_4.sql") ++ SQL.read("mysql-4_4-to-4_5.sql"))
                val  straight4_5 = sqlToTableInfo(SQL.read("mysql-4_5.sql"))
                println("upgradeTo4_5", upgradeTo4_5)
                println("straight4_5", straight4_5)
                assert(upgradeTo4_4 === straight4_4)
                assert(upgradeTo4_5 === straight4_5)
            case SQLServer ⇒
                // No assertions for now (we don't have upgrades yet), but at least test that DDL runs
                sqlToTableInfo(SQL.read("sqlserver-4_6.sql"))
            case _ ⇒ ???
        }
    }
}
