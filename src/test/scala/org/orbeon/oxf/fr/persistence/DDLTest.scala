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

import java.sql.Connection
import org.junit.Test
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.scalatest.junit.AssertionsForJUnit

class DDLTest extends ResourceManagerTestBase with AssertionsForJUnit with DatabaseConnection {


    def withNewDatabase[T](block: Connection ⇒ T): T = {
        try {
            val createUserAndDatabase = Seq(
                "create user orbeon_ddl@localhost identified by 'orbeon_ddl'",
                "create database orbeon_ddl",
                "grant all privileges on orbeon_ddl.* to orbeon_ddl@localhost"
            )
            asRoot(createUserAndDatabase foreach _.createStatement.executeUpdate)
            asOrbeon(block)
        } finally {
            val dropUserAndDatabase = Seq(
                "drop user orbeon_ddl@localhost",
                "drop database orbeon_ddl"
            )
            asRoot(dropUserAndDatabase foreach _.createStatement.executeUpdate)
        }
    }

    // Runs the SQL, and returns the DDL for the tables as defined in the database
    def sqlToDDL(connection: Connection, sql: Seq[String]): Map[String, String] = {
        val statement = connection.createStatement
        getTableNames(connection).map { tableName ⇒
            val tableResultSet = statement.executeQuery("show create table " + tableName)
            tableResultSet.next()
            val tableDDL = tableResultSet.getString(2)
            (tableName, tableDDL)
        }.toMap
    }

    @Test def createAndUpgradeTest(): Unit = {
        val updateDDL = withNewDatabase(sqlToDDL(_, readSQL(s"$Base/mysql-4_3.sql") ++ readSQL(s"$Base/mysql-4_3-to-4_4.sql")))
        val createDDL = withNewDatabase(sqlToDDL(_, readSQL(s"$Base/mysql-4_4.sql")))
        assert(updateDDL === createDDL)
    }
}
