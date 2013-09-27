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

import java.io.{StringWriter, InputStreamReader}
import java.sql.{Statement, DriverManager}
import org.junit.Test
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.orbeon.oxf.util.ScalaUtils._
import org.scalatest.junit.AssertionsForJUnit
import scala.collection.mutable.ListBuffer

class DDLTest extends ResourceManagerTestBase with AssertionsForJUnit {

    val Base = "oxf:/apps/fr/persistence/relational/ddl"

    // Execute statements
    def asRoot  [T](block: Statement ⇒ T): T = asUser("root", None, block)
    def asOrbeon[T](block: Statement ⇒ T): T = asUser("orbeon_ddl", Some("orbeon_ddl"), block)
    def asUser[T](user: String, database: Option[String], block: Statement ⇒ T): T = {
        val databaseString = database getOrElse ""
        val url = s"jdbc:mysql://localhost/$databaseString?user=$user"
        useAndClose(DriverManager.getConnection(url)) { connection ⇒
            block(connection.createStatement())
        }
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

    def withNewDatabase[T](block: Statement ⇒ T): T = {
        try {
            val createUserAndDatabase = Seq(
                "create user orbeon_ddl",
                "create database orbeon_ddl",
                "grant all privileges on orbeon_ddl.* to orbeon_ddl@localhost"
            )
            asRoot(createUserAndDatabase foreach _.executeUpdate)
            asOrbeon(block)
        } finally {
            val dropUserAndDatabase = Seq(
                "drop user orbeon_ddl",
                "drop database orbeon_ddl"
            )
            asRoot(dropUserAndDatabase foreach _.executeUpdate)
        }
    }

    // Runs the SQL, and returns the DDL for the tables as defined in the database
    def sqlToDDL(statement: Statement, sql: Seq[String]): Map[String, String] = {
        sql foreach statement.executeUpdate
        val tableNames = ListBuffer[String]()
        val tablesResultSet = statement.executeQuery("show tables")
        while (tablesResultSet.next()) tableNames += tablesResultSet.getString(1)
        tableNames.map { tableName ⇒
            val tableResultSet = statement.executeQuery(s"show create table $tableName")
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
