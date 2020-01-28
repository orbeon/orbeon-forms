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

import org.orbeon.io.IOUtils.useAndClose
import java.sql.{Connection, ResultSet}

import org.orbeon.oxf.fr.persistence.relational.Provider

object LockSql {

  case class Lease(
    timeout  : Int,
    lockInfo : LockInfo
  )

  def readLease(
    connection  : Connection,
    provider    : Provider,
    reqDataPart : DataPart
  )             : Option[Lease] = {
    val timeoutExpr = Provider.secondsTo(provider, "expiration")
    val sql =
      s"""SELECT username, groupname,
         |       $timeoutExpr AS timeout
         |  FROM orbeon_form_data_lease
         | WHERE document_id = ?
       """.stripMargin
    useAndClose(connection.prepareStatement(sql)) { ps =>
      ps.setString(1, reqDataPart.documentId)
      useAndClose(ps.executeQuery()) { resultSet =>
        if (resultSet.next()) {
          Some(Lease(
            timeout  = resultSet.getInt("timeout"),
            lockInfo = LockInfo(
              username  = resultSet.getString("username"),
              groupname = Option(resultSet.getString("groupname"))
            )
          ))
        } else {
          None
        }
      }
    }
  }

  def updateLease(
    connection  : Connection,
    provider    : Provider,
    reqDataPart : DataPart,
    username    : String,
    groupname   : Option[String],
    timeout     : Int
  )             : Unit = {
    val sql =
      s"""UPDATE orbeon_form_data_lease
         |   SET username    = ?,
         |       groupname   = ?,
         |       expiration  = ${Provider.dateIn(provider)}
         | WHERE document_id = ?
       """.stripMargin
    useAndClose(connection.prepareStatement(sql)) { ps =>
      ps.setString(1, username)
      ps.setString(2, groupname.orNull)
      ps.setInt   (3, timeout)
      ps.setString(4, reqDataPart.documentId)
      ps.executeUpdate()
    }
  }

  def createLease(
    connection  : Connection,
    provider    : Provider,
    reqDataPart : DataPart,
    username    : String,
    groupname   : Option[String],
    timeout     : Int
  )             : Unit = {
    val sql =
      s"""INSERT
         |  INTO orbeon_form_data_lease (
         |           document_id,
         |           username,
         |           groupname,
         |           expiration
         |       )
         |VALUES (?, ?, ?, ${Provider.dateIn(provider)})
       """.stripMargin
    useAndClose(connection.prepareStatement(sql)) { ps =>
      ps.setString(1, reqDataPart.documentId)
      ps.setString(2, username)
      ps.setString(3, groupname.orNull)
      ps.setInt   (4, timeout)
      ps.executeUpdate()
    }
  }

  def removeLease(
    connection  : Connection,
    reqDataPart : DataPart
  )             : Unit = {
    val sql =
      s"""DELETE
         |  FROM orbeon_form_data_lease
         | WHERE document_id = ?
         |
       """.stripMargin
    useAndClose(connection.prepareStatement(sql)) { ps =>
      ps.setString(1, reqDataPart.documentId)
      ps.executeUpdate()
    }
  }

}
