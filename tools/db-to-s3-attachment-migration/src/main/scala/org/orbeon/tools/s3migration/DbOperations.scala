/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.tools.s3migration

import cats.effect.{IO, Resource}
import cats.implicits.catsSyntaxOptionId
import org.orbeon.oxf.fr.persistence.attachments.{PathInformation, S3CRUD}
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.{AppForm, FormOrData}
import org.orbeon.oxf.util.CoreUtils.*

import java.io.InputStream
import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}
import java.util.concurrent.ArrayBlockingQueue
import scala.util.Using


sealed trait RowKey {
  def tableName    : String
  def formOrData   : FormOrData
  def appForm      : AppForm
  def version      : Int
  def draft        : Boolean
  def documentIdOpt: Option[String]
  def filename     : String

  private lazy val pathInformation = PathInformation(
    appForm       = appForm,
    formOrData    = formOrData,
    draft         = draft,
    documentIdOpt = documentIdOpt,
    versionOpt    = version.some,
    filenameOpt   = filename.some
  )

  def s3Key(basePath: String): String =
    S3CRUD.key(basePath, pathInformation)
}

case class DataRowKey(
  appForm   : AppForm,
  version   : Int,
  draft     : Boolean,
  documentId: String,
  filename  : String
) extends RowKey {

  val tableName    : String         = "orbeon_form_data_attach"
  val formOrData   : FormOrData     = FormOrData.Data
  val documentIdOpt: Option[String] = documentId.some
}

case class DefinitionRowKey(
  appForm : AppForm,
  version : Int,
  filename: String
) extends RowKey {

  val tableName    : String         = "orbeon_form_definition_attach"
  val formOrData   : FormOrData     = FormOrData.Form
  val draft        : Boolean        = false
  val documentIdOpt: Option[String] = None
}

class ConnectionPool(config: DbConfig, size: Int) extends AutoCloseable {

  private val available = new ArrayBlockingQueue[Connection](size)
  private val isSQLite  = config.provider == Provider.SQLite

  Class.forName(config.driverClass)

  (1 to size).foreach { _ =>
    val conn = DriverManager.getConnection(config.url, config.user, config.password)
    Using.Manager { use =>
      // Enable WAL mode for SQLite to allow concurrent reads and writes on separate connections
      if (isSQLite)
        use(conn.createStatement()).execute("PRAGMA journal_mode=WAL")
      config.initSqlOpt.foreach { sql =>
        use(conn.createStatement()).execute(sql)
      }
    }.get
    conn.setAutoCommit(false)
    available.put(conn)
  }

  private def borrow()                 : Connection = available.take()
  private def release(conn: Connection): Unit       = available.put(conn)

  def withConnection[T](block: Connection => T): T = {
    val conn = borrow()
    try block(conn) finally release(conn)
  }

  override def close(): Unit = {
    val drain = new java.util.ArrayList[Connection]()
    available.drainTo(drain)
    drain.forEach { conn =>
      try conn.commit() catch { case _: Exception => }
      conn.close()
    }
  }
}

object DbOperations {

  def countDataRows(connection: Connection): Int =
    countRows(connection, "SELECT COUNT(*) FROM orbeon_form_data_attach WHERE file_content IS NOT NULL")

  def countDefinitionRows(connection: Connection): Int =
    countRows(connection, "SELECT COUNT(*) FROM orbeon_form_definition_attach WHERE file_content IS NOT NULL")

  private def countRows(connection: Connection, sql: String): Int =
    Using.Manager { use =>
      val stmt = use(connection.createStatement())
      val rs   = use(stmt.executeQuery(sql))
      rs.next()
      rs.getInt(1)
    }.get

  def streamDataRowKeys(connection: Connection): fs2.Stream[IO, DataRowKey] =
    streamResultSet(
      connection,
      """SELECT app, form, form_version, document_id, draft, file_name
        |  FROM orbeon_form_data_attach
        | WHERE file_content IS NOT NULL
        |""".stripMargin
    ) { rs =>
      DataRowKey(
        appForm    = AppForm(app = rs.getString("app"), form = rs.getString("form")),
        version    = rs.getInt("form_version"),
        draft      = rs.getString("draft") == "Y",
        documentId = rs.getString("document_id"),
        filename   = rs.getString("file_name")
      )
    }

  def streamDefinitionRowKeys(connection: Connection): fs2.Stream[IO, DefinitionRowKey] =
    streamResultSet(
      connection,
      """SELECT app, form, form_version, file_name
        |  FROM orbeon_form_definition_attach
        | WHERE file_content IS NOT NULL
        |""".stripMargin
    ) { rs =>
      DefinitionRowKey(
        appForm  = AppForm(app = rs.getString("app"), form = rs.getString("form")),
        version  = rs.getInt("form_version"),
        filename = rs.getString("file_name")
      )
    }

  // Streams rows from a SQL query, managing the PreparedStatement and ResultSet lifecycle.
  // The ResultSet stays open while elements are pulled, and is closed when the stream terminates.
  private def streamResultSet[A](connection: Connection, sql: String)(readRow: ResultSet => A): fs2.Stream[IO, A] =
    fs2.Stream.resource(
      Resource.make(
        IO.blocking {
          val stmt = connection.prepareStatement(sql)
          val rs   = stmt.executeQuery()
          (stmt, rs)
        }
      ) { case (stmt, rs) =>
        IO.blocking { rs.close(); stmt.close() }
      }
    ).flatMap { case (_, rs) =>
      fs2.Stream
        .repeatEval(IO.blocking(if (rs.next()) Some(readRow(rs)) else None))
        .unNoneTerminate
    }

  // Streams content from the database and passes the InputStream and content length to the block.
  // Returns None if the row was already nullified (e.g. by a concurrent run).
  def withContentStream[T](
    connection: Connection,
    provider  : Provider,
    key       : RowKey
  )(
    block     : (InputStream, Long) => T
  ): Option[T] = {

    val (sql, setParams) = contentQueryAndParams(provider, key)

    Using.Manager { use =>
      val stmt = use(connection.prepareStatement(sql))
      setParams(stmt)

      val rs = use(stmt.executeQuery())
      rs.next().flatOption {
        Option(rs.getBinaryStream("file_content")).map { stream =>
          use(stream)
          block(stream, rs.getLong("content_length"))
        }
      }
    }.get
  }

  private def contentQueryAndParams(provider: Provider, key: RowKey): (String, PreparedStatement => Unit) = {

    val lengthExpr = Provider.binarySize(provider, "file_content", "content_length")

    key match {
      case dataRowKey: DataRowKey =>

        val sql =
          s"""SELECT file_content, $lengthExpr
             |  FROM orbeon_form_data_attach
             | WHERE app          = ?
             |   AND form         = ?
             |   AND form_version = ?
             |   AND document_id  = ?
             |   AND file_name    = ?
             |   AND file_content IS NOT NULL
             |""".stripMargin

        val setParams = (stmt: PreparedStatement) => {
          stmt.setString(1, dataRowKey.appForm.app)
          stmt.setString(2, dataRowKey.appForm.form)
          stmt.setInt   (3, dataRowKey.version)
          stmt.setString(4, dataRowKey.documentId)
          stmt.setString(5, dataRowKey.filename)
        }

        (sql, setParams)

      case definitionRowKey: DefinitionRowKey =>

        val sql =
          s"""SELECT file_content, $lengthExpr
             |  FROM orbeon_form_definition_attach
             | WHERE app          = ?
             |   AND form         = ?
             |   AND form_version = ?
             |   AND file_name    = ?
             |   AND file_content IS NOT NULL
             |""".stripMargin

        val setParams = (stmt: PreparedStatement) => {
          stmt.setString(1, definitionRowKey.appForm.app)
          stmt.setString(2, definitionRowKey.appForm.form)
          stmt.setInt   (3, definitionRowKey.version)
          stmt.setString(4, definitionRowKey.filename)
        }

        (sql, setParams)
    }
  }

  def nullifyRow(connection: Connection, key: RowKey): Unit = {

    val (sql, setParams) = key match {
      case dataRowKey: DataRowKey =>
        val sql =
          """UPDATE orbeon_form_data_attach
            |   SET file_content = NULL
            | WHERE app          = ?
            |   AND form         = ?
            |   AND form_version = ?
            |   AND document_id  = ?
            |   AND file_name    = ?
            |   AND file_content IS NOT NULL
            |""".stripMargin

        val setParams = (stmt: PreparedStatement) => {
          stmt.setString(1, dataRowKey.appForm.app)
          stmt.setString(2, dataRowKey.appForm.form)
          stmt.setInt   (3, dataRowKey.version)
          stmt.setString(4, dataRowKey.documentId)
          stmt.setString(5, dataRowKey.filename)
        }

        (sql, setParams)

      case definitionRowKey: DefinitionRowKey =>
        val sql =
          """UPDATE orbeon_form_definition_attach
            |   SET file_content = NULL
            | WHERE app          = ?
            |   AND form         = ?
            |   AND form_version = ?
            |   AND file_name    = ?
            |   AND file_content IS NOT NULL
            |""".stripMargin

        val setParams = (stmt: PreparedStatement) => {
          stmt.setString(1, definitionRowKey.appForm.app)
          stmt.setString(2, definitionRowKey.appForm.form)
          stmt.setInt   (3, definitionRowKey.version)
          stmt.setString(4, definitionRowKey.filename)
        }

        (sql, setParams)
    }

    Using.Manager { use =>
      val stmt = use(connection.prepareStatement(sql))
      setParams(stmt)
      stmt.executeUpdate()
    }.get
  }
}
