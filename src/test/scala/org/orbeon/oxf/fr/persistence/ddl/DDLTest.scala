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
import org.orbeon.oxf.fr.persistence.DB
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.scalatest.junit.AssertionsForJUnit

/**
 * Test the DDL we provide to create and update databases.
 */
class DDLTest extends ResourceManagerTestBase with AssertionsForJUnit {

    private def withNewDatabase[T](block: Connection ⇒ T): T = {

        try {
            val createUserAndDatabase = Seq(
                "create user orbeon_ddl@localhost identified by 'orbeon_ddl'",
                "create database orbeon_ddl",
                "grant all privileges on orbeon_ddl.* to orbeon_ddl@localhost"
            )
            DB.asRoot(createUserAndDatabase foreach _.createStatement.executeUpdate)
            DB.asOrbeon(block)
        } finally {
            val dropUserAndDatabase = Seq(
                "drop user orbeon_ddl@localhost",
                "drop database orbeon_ddl"
            )
            DB.asRoot(dropUserAndDatabase foreach _.createStatement.executeUpdate)
        }
    }

    // Runs the SQL, and returns the DDL for the tables as defined in the database
    private def sqlToDDL(connection: Connection, sql: Seq[String]): Map[String, String] = {
        val statement = connection.createStatement
        sql foreach statement.executeUpdate
        DB.getTableNames(connection).map { tableName ⇒
            val tableResultSet = statement.executeQuery("show create table " + tableName)
            tableResultSet.next()
            val tableDDL = tableResultSet.getString(2)
            (tableName, tableDDL)
        }.toMap
    }

    @Test def createAndUpgradeTest(): Unit = {
        val updateDDL = withNewDatabase(sqlToDDL(_, DB.readSQL(s"${DB.Base}/mysql-4_3.sql") ++ DB.readSQL(s"${DB.Base}/mysql-4_3-to-4_4.sql")))
        val createDDL = withNewDatabase(sqlToDDL(_, DB.readSQL(s"${DB.Base}/mysql-4_4.sql")))
        assert(updateDDL === createDDL)
    }
}
