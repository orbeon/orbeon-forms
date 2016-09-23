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
package org.orbeon.oxf.fr.persistence.relational

import java.sql.{Connection, ResultSet}
import javax.xml.transform.stream.StreamSource

import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.saxon.om.DocumentInfo

object Provider {

  sealed trait Provider    extends Product with Serializable {
    val name: String
    def uri : String = "/fr/service/" + name
  }

  case object  MySQL       extends Provider { val name = "mysql"      }
  case object  PostgreSQL  extends Provider { val name = "postgresql" }

  def providerFromToken(token: String): Provider = {
    val AllProviders = List(MySQL, PostgreSQL)
    val providerOpt = AllProviders.find(_.name == token)
    providerOpt.getOrElse(throw new IllegalStateException)
  }

  def xmlCol(provider: Provider, tableName: String) =
    provider match {
      case PostgreSQL ⇒ s"$tableName.xml as xml"
      case _          ⇒ s"$tableName.xml xml"
    }

  def xmlContains(provider: Provider): String =
    provider match {
      case MySQL      ⇒ "xml like ?"
      case PostgreSQL ⇒ "xml::text ilike ?"
    }

  def xmlContainsParam(provider: Provider, param: String): String =
    provider match {
      case MySQL | PostgreSQL ⇒ s"%$param%"
      case _                  ⇒ param
    }

  def textContains(provider: Provider, colName: String): String =
    provider match {
      case MySQL ⇒
        // LIKE is case insensitive on MySQL and SQL Server
        s"$colName LIKE ?"
      case PostgreSQL ⇒
        // PostgreSQL has ILIKE as a case insensitive version of LIKE
        s"$colName ILIKE ?"
    }

  def readXmlColumn(provider: Provider, resultSet: ResultSet): DocumentInfo = {
    provider match {
      case PostgreSQL ⇒
        val xmlString = resultSet.getString("xml")
        TransformerUtils.stringToTinyTree(XPath.GlobalConfiguration, xmlString, false, false)
      case _ ⇒
        val dataClob = resultSet.getClob("xml")
        val source = new StreamSource(dataClob.getCharacterStream)
        TransformerUtils.readTinyTree(XPath.GlobalConfiguration, source, false)
    }
  }

  def seqNextVal(connection: Connection, provider: Provider): Int = {
    val nextValSql = {
      useAndClose(connection.prepareStatement("INSERT INTO orbeon_seq VALUES ()"))(_.executeUpdate())
      "SELECT max(val) FROM orbeon_seq"
    }
    useAndClose(connection.prepareStatement(nextValSql)){ statement ⇒
      useAndClose(statement.executeQuery()) { resultSet ⇒
        resultSet.next()
        resultSet.getInt(1)
      }
    }
  }
}
