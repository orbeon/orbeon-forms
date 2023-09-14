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

import enumeratum.EnumEntry.Lowercase
import enumeratum._
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.io.IOUtils._
import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.DocumentInfo

import java.io.StringReader
import java.sql.{Connection, ResultSet}
import javax.xml.transform.stream.StreamSource

import java.io.StringReader


sealed trait Provider extends EnumEntry with Lowercase

object Provider extends Enum[Provider] {

  val values = findValues

  case object MySQL      extends Provider
  case object PostgreSQL extends Provider

  def xmlCol(provider: Provider, tableName: String): String =
    provider match {
      case PostgreSQL => s"$tableName.xml as xml"
      case _          => s"$tableName.xml xml"
    }

  def xmlColSelect(provider: Provider, tableName: String): String =
    provider match {
      case PostgreSQL => s"$tableName.xml as xml"
      case _          => s"$tableName.xml xml"
    }

  def xmlColUpdate(provider: Provider): String =
    provider match {
      case _      => "xml"
    }

  def xmlValUpdate(provider: Provider): String =
    provider match {
      case PostgreSQL => "XMLPARSE( DOCUMENT ? )"
      case _          => "?"
    }

  // We would like this search to be case-insensitive, but it is not on all databases:
  // - Oracle and SQL Server: based on the database collation
  // - MySQL: case-insensitive, as we set a case-insensitive collation on the `xml` column
  // - PostgreSQL: case-insensitive, as use ILIKE
  def xmlContains(provider: Provider): String =
    provider match {
      case MySQL      => "instr(xml, ?) > 0"
      case PostgreSQL => "xml::text ilike ?"
    }

  private def paramForLike(param: String, surroundingPercents: Boolean): String = {
    val escapedParam =
      param.replace("\\", "\\\\")
           .replace("%" , "\\%" )
           .replace("_" , "\\_" )
     if (surroundingPercents) s"%$escapedParam%" else escapedParam
  }

  def xmlContainsParam(provider: Provider, param: String): String =
    provider match {
      case PostgreSQL => s"%$param%"
      case _          => param
    }

  def textContains(provider: Provider, colName: String): String =
    provider match {
      case MySQL =>
        // LIKE is case insensitive on MySQL and SQL Server
        s"$colName LIKE ?"
      case SQLServer =>
        s"$colName LIKE ? ESCAPE '\\'"
      case PostgreSQL =>
        // PostgreSQL has ILIKE as a case insensitive version of LIKE
        s"$colName ILIKE ?"
    }

  def textContainsParam(provider: Provider, param: String): String =
    paramForLike(param, surroundingPercents = true)

  def textEquals(provider: Provider, colName: String): String =
    provider match {
      // SQL Server doesn't support the `=` operator on `ntext`
      // Oracle requires `LIKE` to avoid "ORA-00932: inconsistent datatypes: expected - got CLOB"
      case _                  => s"$colName =    ?"
    }

  def textEqualsParam(provider: Provider, param: String): String =
    provider match {
      case _                  => param
    }

  def readXmlColumn(
    provider              : Provider,
    resultSet             : ResultSet,
    returnMutableDocument : Boolean = false
  ): DocumentInfo = {

    val xmlReader =
      provider match {
        case PostgreSQL =>
          val xmlString = resultSet.getString("xml")
          new StringReader(xmlString)
        case _ =>
          val dataClob = resultSet.getClob("xml")
          dataClob.getCharacterStream
      }

    val source = new StreamSource(xmlReader)
    if (returnMutableDocument) {
      val xmlDom = TransformerUtils.readOrbeonDom(source, false)
      new DocumentWrapper(xmlDom, null, XPath.GlobalConfiguration)
    } else {
      TransformerUtils.readTinyTree(XPath.GlobalConfiguration, source, false)
    }
  }

  def seqNextVal(connection: Connection, provider: Provider): Int = {
    val nextValSql = provider match {
      case _ =>
        val insertSql = provider match {
          case MySQL => "INSERT INTO orbeon_seq VALUES ()"
          case _     => "INSERT INTO orbeon_seq DEFAULT VALUES"
        }
        useAndClose(connection.prepareStatement(insertSql))(_.executeUpdate())
        "SELECT max(val) FROM orbeon_seq"
    }
    useAndClose(connection.prepareStatement(nextValSql)){ statement =>
      useAndClose(statement.executeQuery()) { resultSet =>
        resultSet.next()
        resultSet.getInt(1)
      }
    }
  }

  def withLockedTable(
    connection : Connection,
    provider   : Provider,
    tableName  : String,
    thunk      : () => Unit
  ): Unit = {
    val lockSQL = provider match {
      case MySQL      => s"LOCK TABLES $tableName WRITE"
      case PostgreSQL => s"LOCK TABLE $tableName IN EXCLUSIVE MODE"
      case _          => throw new UnsupportedOperationException
    }
    // See https://github.com/orbeon/orbeon-forms/issues/3866
    useAndClose(connection.createStatement())(_.execute(lockSQL))
    try {
      thunk()
    } finally {
      val unlockSQLOpt = provider match {
        case MySQL => Some(s"UNLOCK TABLES")
        case _     => None
      }
      unlockSQLOpt.foreach(unlockSQL =>
        // See https://github.com/orbeon/orbeon-forms/issues/3866
        useAndClose(connection.createStatement())(_.execute(unlockSQL)))
    }
  }

  def secondsTo(provider: Provider, date: String): String =
    provider match {
      case MySQL      => s"TIMESTAMPDIFF(second, CURRENT_TIMESTAMP, $date)"
      case PostgreSQL => s"EXTRACT(EPOCH FROM ($date - CURRENT_TIMESTAMP))"
      case _          => throw new UnsupportedOperationException
    }

  def dateIn(provider: Provider): String =
    provider match {
      case MySQL        => "DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? second)"
      case PostgreSQL   => "CURRENT_TIMESTAMP + (interval '1' second * ?)"
      case _            => throw new UnsupportedOperationException
    }

  case class RowNumSQL(
    table   : Option[String],
    col     : String,
    orderBy : String
  )

  // MySQL < 8 lacks row_number, see http://stackoverflow.com/a/1895127/5295
  def rowNumSQL(
    provider   : Provider,
    connection : Connection,
    tableAlias : String
  ): RowNumSQL = {

    val mySQLMajorVersion =
      (provider == MySQL).option {
        val mySQLVersion = {
          val sql = "SHOW VARIABLES LIKE \"version\""
          useAndClose(connection.prepareStatement(sql)) { ps =>
            useAndClose(ps.executeQuery()) { rs =>
              rs.next()
              rs.getString("value")
            }
          }
        }
        mySQLVersion.splitTo(".").head.toInt
      }

    mySQLMajorVersion match {
      case Some(v) if v < 8 =>
        RowNumSQL(
          table   = Some("(select @rownum := 0) r"),
          col     = "@rownum := @rownum + 1 row_num",
          orderBy = s"ORDER BY $tableAlias.last_modified_time DESC"
        )
      case _ =>
        RowNumSQL(
          table   = None,
          col     = s"row_number() over (order by $tableAlias.last_modified_time desc) row_num",
          orderBy = ""
        )
    }
  }

  private val FlatViewMustDelete: Set[Provider] = Set(PostgreSQL)

  def flatViewDelete(provider: Provider): Boolean =
    FlatViewMustDelete(provider)

  def flatViewExistsQuery(provider: Provider): String =
    provider match{
      case PostgreSQL   =>
                          s"""|SELECT *
                              |  FROM information_schema.views
                              | WHERE     table_name   = ?
                              |       AND table_schema = current_schema()
                              |""".stripMargin
      case _            => throw new UnsupportedOperationException
    }

  // On PostgreSQL, the name is stored in lower case in `information_schema.views`
  def flatViewExistsParam(provider: Provider, viewName: String): String =
    if (provider == PostgreSQL) viewName.toLowerCase else viewName

  def flatViewExtractFunction(provider: Provider, path: String): String =
    provider match {
      case PostgreSQL => s"(xpath('/*/$path/text()', d.xml))[1]::text"
      case _          => throw new UnsupportedOperationException
    }

  def flatViewCreateView(provider: Provider, appForm: AppForm, version: String, viewName: String, cols: String): String = {

    def escapeSQL(s: String): String =
      s.replace("'", "''")

    val orReplace =
      provider match {
        case _          => ""
      }

    s"""|CREATE  $orReplace VIEW $viewName AS
        |SELECT  $cols
        |  FROM  orbeon_form_data d,
        |        (
        |            SELECT   max(last_modified_time) last_modified_time,
        |                     app, form, form_version, document_id
        |              FROM   orbeon_form_data d
        |             WHERE       app          = '${escapeSQL(appForm.app)}'
        |                     AND form         = '${escapeSQL(appForm.form)}'
        |                     AND form_version = $version
        |                     AND draft        = 'N'
        |            GROUP BY app, form, form_version, document_id
        |        ) m
        | WHERE      d.last_modified_time = m.last_modified_time
        |        AND d.app                = m.app
        |        AND d.form               = m.form
        |        AND d.form_version       = m.form_version
        |        AND d.document_id        = m.document_id
        |        AND d.deleted            = 'N'
        |""".stripMargin
  }


  val FlatViewSupportedProviders: Set[Provider] = Set(PostgreSQL)

  def idColGetter(provider: Provider): Option[String] =
    provider match {
      case _      => None
    }

  def concat(provider: Provider, args: String*): String =
    provider match {
      case _ =>
        s"concat(${args.mkString(", ")})"
    }

  def partialBinaryMaxLength(provider: Provider): Int =
    provider match {
      case _      => Int.MaxValue
    }

  def partialBinary(provider: Provider, columnName: String, alias: String, offset: Long, length: Option[Long]): String =
    provider match {
      case _         => s"substring($columnName, ${offset + 1}${length.map(l => s", $l").getOrElse("")}) as $alias"
    }

  def binarySize(provider: Provider, columnName: String, alias: String): String =
    provider match {
      case _         => s"length($columnName) as $alias"
    }
}
