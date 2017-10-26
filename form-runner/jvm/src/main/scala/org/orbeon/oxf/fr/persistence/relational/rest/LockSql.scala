/**
 * Copyright (C) 2017 Orbeon, Inc.
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

import org.orbeon.oxf.util.IOUtils.useAndClose
import java.sql.{Connection, ResultSet}

import org.orbeon.oxf.externalcontext.Credentials


object LockSql {

  case class Lease(
    username  : String,
    groupname : Option[String],
    expired   : Boolean
  )

  def readLease(
    connection  : Connection,
    reqDataPart : DataPart
  )             : Option[Lease] = {
    val sql =
      s"""SELECT username, groupname,
         |       CASE
         |           WHEN expiration <= CURRENT_TIMESTAMP
         |           THEN 1
         |           ELSE 0
         |       END
         |       AS expired
         |  FROM orbeon_form_data_lease (TABLOCKX)
         | WHERE document_id = ?
       """.stripMargin
    useAndClose(connection.prepareStatement(sql)) { ps ⇒
      ps.setString(1, reqDataPart.documentId)
      useAndClose(ps.executeQuery()) { resultSet ⇒
        if (resultSet.next()) {
          Some(Lease(
            username  = resultSet.getString("username"),
            groupname = Option(resultSet.getString("groupname")),
            expired   = resultSet.getInt("expired") == 1
          ))
        } else {
          None
        }
      }
    }
  }

  def updateLease(
    connection  : Connection,
    reqDataPart : DataPart,
    username    : String,
    groupname   : Option[String]
  )             : Unit = {
    val sql =
      s"""UPDATE orbeon_form_data_lease
         |   SET username    = ?,
         |       groupname   = ?,
         |       expiration  = DATEADD(minute, ?, CURRENT_TIMESTAMP)
         | WHERE document_id = ?
       """.stripMargin
    useAndClose(connection.prepareStatement(sql)) { ps ⇒
      ps.setString(1, username)
      ps.setString(2, groupname.orNull)
      ps.setInt   (3, 42)
      ps.setString(4, reqDataPart.documentId)
      ps.executeUpdate()
    }
  }

  def createLease(
    connection  : Connection,
    reqDataPart : DataPart,
    username    : String,
    groupname   : Option[String]
  )             : Unit = {
    val sql =
      s"""INSERT
         |  INTO orbeon_form_data_lease (
         |           document_id,
         |           username,
         |           groupname,
         |           expiration
         |       )
         |VALUES (?, ?, ?, DATEADD(minute, ?, CURRENT_TIMESTAMP)) ;
       """.stripMargin
    useAndClose(connection.prepareStatement(sql)) { ps ⇒
      ps.setString(1, reqDataPart.documentId)
      ps.setString(2, username)
      ps.setString(3, groupname.orNull)
      ps.setInt   (4, 42)
      ps.executeUpdate()
    }
  }

}
