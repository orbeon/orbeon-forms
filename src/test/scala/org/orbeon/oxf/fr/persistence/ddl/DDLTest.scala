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

    // Runs the SQL, and returns the DDL for the tables as defined in the database
    private def sqlToDDL(sql: Seq[String]): Map[String, String] = {
        withNewDatabase { connection ⇒
            val statement = connection.createStatement
            sql foreach statement.executeUpdate
            Connect.getTableNames(connection).map { tableName ⇒
                val tableResultSet = statement.executeQuery("show create table " + tableName)
                tableResultSet.next()
                val tableDDL = tableResultSet.getString(2)
                (tableName, tableDDL)
            }.toMap
        }
    }

    @Test def createAndUpgradeTest(): Unit = {
        Config.provider match {
            case MySQL ⇒
                val upgradeTo4_4DDL = sqlToDDL(SQL.read("mysql-4_3.sql") ++ SQL.read("mysql-4_3-to-4_4.sql"))
                val  straight4_4DDL = sqlToDDL(SQL.read("mysql-4_4.sql"))
                //val upgradeTo4_5DDL = sqlToDDL(SQL.read("mysql-4_4.sql") ++ SQL.read("mysql-4_4-to-4_5.sql"))
                //val  straight4_5DDL = sqlToDDL(SQL.read("mysql-4_5.sql"))
                assert(upgradeTo4_4DDL === straight4_4DDL)
                //assert(upgradeTo4_5DDL === straight4_5DDL)
            case SQLServer ⇒
                // No assertions, but at least test that DDL runs
                sqlToDDL(SQL.read("sqlserver-4_6.sql"))
            case _ ⇒ ???
        }
    }
}
