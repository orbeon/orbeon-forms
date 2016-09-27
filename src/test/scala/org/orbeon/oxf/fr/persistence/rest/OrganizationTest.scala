/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.rest

import java.sql.Connection

import org.junit.Test
import org.orbeon.oxf.fr.persistence.db.Connect
import org.orbeon.oxf.fr.persistence.relational.crud.{Organization, OrganizationId}
import org.orbeon.oxf.test.{ResourceManagerTestBase, XMLSupport}
import org.orbeon.oxf.util.IOUtils._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory, Logging}
import org.scalatest.junit.AssertionsForJUnit


// Test organization-related code used by the REST API
class OrganizationTest extends ResourceManagerTestBase with AssertionsForJUnit with XMLSupport with Logging {

  private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[OrganizationTest]), true)

  private val CA = Organization(List("usa", "ca"))
  private val SF = Organization(List("usa", "ca", "sf"))

  def recordsCount(connection: Connection): Int = {
    val Sql = "SELECT count(*) FROM orbeon_form_organization"
    useAndClose(connection.prepareStatement(Sql)) { statement ⇒
      useAndClose(statement.executeQuery()) { resultSet ⇒
        resultSet.next()
        resultSet.getInt(1)
      }
    }
  }

  def assertRecordsCountIs(connection: Connection, count: Int): Unit =
    assert(recordsCount(connection) === count)

  // Basic test: write organization and read it back
  @Test def createAndRead(): Unit = {
    Connect.withOrbeonTables("create and read") { (connection, provider) ⇒
      List(CA, SF).foreach { writtenOrganization ⇒
        val orgId = Organization.createIfNecessary(connection, provider, writtenOrganization)
        val readOrganization = Organization.read(connection, orgId)
        assert(readOrganization.get === writtenOrganization)
      }
    }
  }

  @Test def readEmptyWhenNotFound(): Unit = {
    Connect.withOrbeonTables("create and read") { (connection, provider) ⇒
      Organization.createIfNecessary(connection, provider, CA)
      val read = Organization.read(connection, OrganizationId(42))
      assert(read.isEmpty)
    }
  }

  // We reuse the record for an organization
  @Test def orgReuse(): Unit = {
    Connect.withOrbeonTables("create read") { (connection, provider) ⇒

      Organization.createIfNecessary(connection, provider, CA)
      assertRecordsCountIs(connection, 2)

      // Testing the "if necessary" part
      Organization.createIfNecessary(connection, provider, CA)
      assertRecordsCountIs(connection, 2)
    }
  }

}
