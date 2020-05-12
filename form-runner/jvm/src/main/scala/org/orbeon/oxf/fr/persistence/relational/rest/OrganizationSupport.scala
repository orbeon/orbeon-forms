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
package org.orbeon.oxf.fr.persistence.relational.rest

import java.sql.{Connection, ResultSet}

import org.orbeon.oxf.externalcontext.Organization
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.io.IOUtils._

// Operations on organizations
// - When creating data, if we have an organization, we want to create it or return it
// - When reading data, we want to make sure the user

// Type for organization id, which is stored as an int
case class OrganizationId(underlying: Int) extends AnyVal

object OrganizationSupport {

  def createIfNecessary(
    connection   : Connection,
    provider     : Provider,
    organization : Organization
  ): OrganizationId = {

    // See if have this organization already in the database
    val existingOrganization = {

      val sql = {

        // Generate lists for each level of the organization
        def perLevel(f: Int => List[String]): List[String] =
          organization
            .levels
            .zipWithIndex
            .flatMap { case (_, pos) => f(pos + 1) }

        val sqlFrom  = perLevel{pos =>
          List(s"orbeon_organization o$pos")
        }.mkString(", ")
        val sqlWhere = perLevel{pos =>
          // Join organization tables on id
          (pos > 1).list(s"o$pos.id = o${pos -1}.id") ++
          // Depth must match
          List(s"o$pos.depth = ?") ++
          // Name must match
          List(s"o$pos.name = ?")
        }.mkString(" AND ")

        s"SELECT o1.id FROM $sqlFrom WHERE $sqlWhere"
      }

      useAndClose(connection.prepareStatement(sql)) { statement =>
        organization.levels.zipWithIndex.foreach { case (name, pos) =>
            statement.setInt   (pos*2 + 1, organization.levels.length)
            statement.setString(pos*2 + 2, name)
        }
        useAndClose(statement.executeQuery()) { resultSet =>
          val foundExistingOrganization = resultSet.next()
          foundExistingOrganization.option(resultSet.getInt("id"))
        }
      }
    }

    // If not, create the organization in the database
    val intOrganizationId =
      existingOrganization.getOrElse(
        Provider
          .seqNextVal(connection, provider)
          .kestrel { orgId =>
            organization
              .levels
              .zipWithIndex
              .foreach { case (name, pos) =>
                val Sql =
                  """INSERT
                    |  INTO orbeon_organization (id, depth, pos, name)
                    |  VALUES                   (? , ?    , ?  , ?)
                    |  """.stripMargin
                useAndClose(connection.prepareStatement(Sql)) { statement =>
                  statement.setInt   (1, orgId)
                  statement.setInt   (2, organization.levels.length)
                  statement.setInt   (3, pos + 1)
                  statement.setString(4, name)
                  statement.executeUpdate()
                }
              }
          }
      )
    OrganizationId(intOrganizationId)
  }

  def read(connection: Connection, id: OrganizationId): Option[Organization] = {

    val Sql =
      """  SELECT name
        |    FROM orbeon_organization
        |   WHERE id = ?
        |ORDER BY pos
        |""".stripMargin

    useAndClose(connection.prepareStatement(Sql)) { statement =>
      statement.setInt(1, id.underlying)
      useAndClose(statement.executeQuery()) { resultSet =>
        val levels = Iterator.iterateWhile(
          resultSet.next(),
          resultSet.getString("name")
        ).toList
        levels match {
          case Nil => None
          case _   => Some(Organization(levels))
        }
      }
    }
  }

  def readFromResultSet(
    connection: Connection,
    resultSet: ResultSet
  ): Option[(Int, Organization)] = {
    val id        = resultSet.getInt("organization_id")
    val isValidId = ! resultSet.wasNull()
    if (! isValidId) {
      None
    } else {
      val organizationOpt = OrganizationSupport.read(connection, OrganizationId(id))
      organizationOpt.map(id -> _)
    }
  }

}
