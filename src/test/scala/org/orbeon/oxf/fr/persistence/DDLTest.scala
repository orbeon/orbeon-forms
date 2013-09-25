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

import java.sql.DriverManager
import org.junit.{After, Before, Test}
import org.orbeon.oxf.util.ScalaUtils._
import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.oxf.resources.URLFactory
import java.io.{StringWriter, InputStreamReader, OutputStreamWriter}
import org.orbeon.oxf.test.ResourceManagerTestBase

class DDLTest extends ResourceManagerTestBase with AssertionsForJUnit {

    val Base = "oxf:/apps/fr/persistence/relational/ddl"

    // Execute statements
    def asRoot(updates: Seq[String]): Unit = asUser("root", None, updates)
    def asOrbeon(updates: Seq[String]): Unit = asUser("orbeon_test", Some("orbeon_test"), updates)
    def asUser(user: String, database: Option[String], updates: Seq[String]): Unit = {
        val databaseString = database getOrElse ""
        useAndClose(DriverManager.getConnection(s"jdbc:mysql://localhost/$databaseString?user=$user")) { connection ⇒
            val statement = connection.createStatement()
            updates foreach statement.executeUpdate
        }
    }

    // Reads a sequence semicolon-separated of statements from a text file
    def readStatements(url: String): Seq[String] = {
        val inputStream = URLFactory.createURL(url).openStream()
        val reader = new InputStreamReader(inputStream)
        val writer = new StringWriter
        copyReader(reader, writer)
        val statements = writer.toString.split(";")
        statements map (_.trim) filter (_.nonEmpty)
    }

    def withNewDatabase(block: ⇒ Unit) {
        try {
            asRoot(Seq(
                "create user orbeon_test",
                "create database orbeon_test",
                "grant all privileges on orbeon_test.* to orbeon_test@localhost"
            ))
            block
        } finally {
            asRoot(Seq(
                "drop user orbeon_test",
                "drop database orbeon_test"
            ))
        }
    }

    @Test def createAndUpgradeTest(): Unit = {
        withNewDatabase {
            asOrbeon(readStatements(s"$Base/mysql-4_3.sql"))
            asOrbeon(readStatements(s"$Base/mysql-4_3-to-4_4.sql"))
        }
    }

    @Test def createLatest(): Unit = {
        withNewDatabase {
            asOrbeon(readStatements(s"$Base/mysql-4_4.sql"))
        }
    }
}
