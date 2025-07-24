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

import enumeratum.*
import enumeratum.EnumEntry.Lowercase
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.io.IOUtils.*
import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.DocumentInfo

import java.io.StringReader
import java.sql.{Connection, PreparedStatement, ResultSet, Statement}
import javax.xml.transform.stream.StreamSource


sealed trait Provider extends EnumEntry with Lowercase

object Provider extends Enum[Provider] {

  val values = findValues

  case object MySQL      extends Provider
  case object PostgreSQL extends Provider

  case object SQLite     extends Provider

  def xmlColSelect(provider: Provider, tableName: String): String =
    provider match {
      case PostgreSQL | SQLite => s"$tableName.xml as xml"
      case _                   => s"$tableName.xml xml"
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
  // - PostgreSQL: case-insensitive in testing (`LC_CTYPE` being `en_US.utf8`)
  def xmlContains(provider: Provider): String =
    provider match {
      case MySQL      => "instr(xml, ?) > 0"
      case PostgreSQL => "to_tsvector('simple', xml::text) @@ plainto_tsquery('simple', ?)"
      case SQLite     => "xml LIKE ? ESCAPE '\\'" // SQLite doesn't support XML columns
    }

  private def paramForLike(param: String, surroundingPercents: Boolean): String = {
    val escapedParam = param.escaped(Seq("\\", "%", "_"), "\\")
     if (surroundingPercents) s"%$escapedParam%" else escapedParam
  }

  def xmlContainsParam(provider: Provider, param: String): String =
    provider match {
      case SQLite =>
        paramForLike(param, surroundingPercents = true)
      case _ =>
        param
    }

  def textContains(provider: Provider, colName: String): String =
    provider match {
      case MySQL =>
        // LIKE is case insensitive on MySQL, SQL Server, and SQLite
        s"$colName LIKE ?"
      case SQLite =>
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
        case PostgreSQL | SQLite =>
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

  def withLockedTable[T](
    connection : Connection,
    provider   : Provider,
    tableName  : String
  )(
    thunk      : => T
  ): T = {
    val lockSQLOpt = provider match {
      case MySQL                      => Some(s"LOCK TABLES $tableName WRITE")
      case PostgreSQL                 => Some(s"LOCK TABLE $tableName IN EXCLUSIVE MODE")
      case SQLite                     => None // SQLite doesn't support locking tables
      case _                          => throw new UnsupportedOperationException
    }
    // See https://github.com/orbeon/orbeon-forms/issues/3866
    lockSQLOpt.foreach(lockSQL => useAndClose(connection.createStatement())(_.execute(lockSQL)))
    try {
      thunk
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
      case SQLite     => s"(julianday($date) - julianday(CURRENT_TIMESTAMP)) * 24 * 60 * 60"
      case _          => throw new UnsupportedOperationException
    }

  def dateIn(provider: Provider): String =
    provider match {
      case MySQL       => "DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? second)"
      case PostgreSQL  => "CURRENT_TIMESTAMP + (interval '1' second * ?)"
      case SQLite      => "DATETIME(CURRENT_TIMESTAMP, format('+%f seconds', ?))"
      case _           => throw new UnsupportedOperationException
    }

  case class RowNumSQL(
    table   : Option[String],
    col     : String,
    orderBy : String
  )

  // MySQL < 8 lacks row_number, see http://stackoverflow.com/a/1895127/5295
  def rowNumSQL(
    provider       : Provider,
    connection     : Connection,
    orderBy        : String
  ): RowNumSQL = {

    val mySQLMajorVersion =
      (provider == MySQL).option(connection.getMetaData.getDatabaseMajorVersion)

    mySQLMajorVersion match {
      case Some(v) if v < 8 =>
        RowNumSQL(
          table   = Some("(select @rownum := 0) r"),
          col     = "@rownum := @rownum + 1 row_num",
          orderBy = s"ORDER BY $orderBy"
        )
      case _ =>
        RowNumSQL(
          table   = None,
          col     = s"row_number() over (order by $orderBy) row_num",
          orderBy = ""
        )
    }
  }

  private val FlatViewMustDelete: Set[Provider] = Set(MySQL, PostgreSQL)

  def flatViewDelete(provider: Provider): Boolean =
    FlatViewMustDelete(provider)

  def flatViewExistsQuery(provider: Provider): String =
    provider match{
      case MySQL        =>
                          s"""|SELECT *
                              |  FROM information_schema.views
                              | WHERE     table_name   = ?
                              |       AND table_schema = database()
                              |""".stripMargin
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

  def flatViewExtractFunction(provider: Provider, xmlTable: String, xpath: String): String =
    provider match {
      case MySQL      => s"extractvalue($xmlTable.extracted_xml, '$xpath')"
      case PostgreSQL => s"(xpath('$xpath', $xmlTable.extracted_xml))[1]::text"
      case _          => throw new UnsupportedOperationException
    }

  def flatViewCanUnnestArray(provider: Provider): Boolean =
    provider match {
      case PostgreSQL => true
      case _          => false
    }

  def flatViewRepeatedXmlColumn(provider: Provider, xmlTable: String, xpath: String): String =
    provider match {
      case PostgreSQL   => s"unnest(xpath('$xpath', $xmlTable.extracted_xml)) extracted_xml"
      case _            => throw new UnsupportedOperationException
    }

  def flatViewRepeatedXmlTable(provider: Provider, xmlTable: String, xpath: String): String =
    provider match {
      case PostgreSQL => s""
      case _          => throw new UnsupportedOperationException
    }

  def flatViewRowNumberOrderBy(provider: Provider): String =
    provider match {
      case PostgreSQL       => ""
      case _                => throw new UnsupportedOperationException
    }

  def flatViewCreateView(provider: Provider, viewName: String, selectStatement: String): String = {

    val orReplace = provider match {
      case _          => ""
    }

    s"""|CREATE $orReplace VIEW $viewName AS
        |$selectStatement
        |""".stripMargin
  }

  def flatViewFormDataSelectStatement(
    appForm   : AppForm,
    version   : Int,
    tableAlias: String,
    columns   : Seq[String]
  ): String = {

    def escapeSQL(s: String): String = s.replace("'", "''")

    s"""|      SELECT ${columns.mkString(", ")}
        |        FROM orbeon_form_data $tableAlias
        |  INNER JOIN orbeon_i_current c ON $tableAlias.id = c.data_id
        |       WHERE $tableAlias.app          = '${escapeSQL(appForm.app)}'
        |         AND $tableAlias.form         = '${escapeSQL(appForm.form)}'
        |         AND $tableAlias.form_version = $version
        |         AND $tableAlias.draft        = 'N'
        |""".stripMargin
  }

  val FlatViewSupportedProviders: Set[Provider] = Set(MySQL, PostgreSQL)

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

  def distinctVal(provider: Provider, columnName: String, alias: String): String =
    provider match {
      case _         => s"DISTINCT $columnName AS $alias"
    }

  def executeUpdateAndReturnGeneratedId(
    connection         : java.sql.Connection,
    provider           : Provider,
    table              : String,
    sql                : String
  )(
    beforeExecuteUpdate: PreparedStatement => Unit
  ): Option[Int] = {

    val tableWithId = table == "orbeon_form_data"

    val preparedStatement =
      if (tableWithId) {
        provider match {
          case Provider.MySQL  => connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
          case Provider.SQLite => connection.prepareStatement(sql)
          case _               => connection.prepareStatement(sql, Array("id"))
        }
      } else {
        connection.prepareStatement(sql)
      }

    val idFromGeneratedKeysOpt = useAndClose(preparedStatement) { ps =>
      beforeExecuteUpdate(ps)
      ps.executeUpdate()

      // All databases except SQLite support getGeneratedKeys
      (tableWithId && provider != Provider.SQLite).flatOption {
        val generatedKeys = ps.getGeneratedKeys
        generatedKeys.next().option(generatedKeys.getInt(1))
      }
    }

    // Special case for SQLite
    val idFromLastInsertRowidOpt = (tableWithId && provider == Provider.SQLite).flatOption {
      useAndClose(connection.prepareStatement("SELECT last_insert_rowid()")) { ps =>
        val rs = ps.executeQuery()
        rs.next().option(rs.getInt(1))
      }
    }

    idFromGeneratedKeysOpt orElse idFromLastInsertRowidOpt
  }
}
