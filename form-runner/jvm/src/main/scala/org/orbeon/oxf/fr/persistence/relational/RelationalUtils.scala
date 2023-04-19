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
package org.orbeon.oxf.fr.persistence.relational

import org.orbeon.errorified.Exceptions
import org.orbeon.io.IOUtils._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.fr.persistence.PersistenceMetadataSupport
import org.orbeon.oxf.fr.{AppForm, FormDefinitionVersion, FormRunner, Names}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory, Logging, NetUtils}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._

import java.sql.{Connection, ResultSet}
import javax.naming.{Context, InitialContext}
import javax.sql.DataSource
import scala.util.control.NonFatal


object RelationalUtils extends Logging {

  implicit val Logger = new IndentedLogger(LoggerFactory.createLogger("org.orbeon.relational"))

  def withConnection[T](thunk: Connection => T): T =
    withConnection(getDataSourceNameFromHeaders)(thunk)

  def withConnection[T](datasourceName: String)(thunk: Connection => T): T =
    useAndClose(getConnection(getDataSource(datasourceName))) { connection =>
      try {
        val result = withDebug("executing block with connection")(thunk(connection))
        debug("about to commit")
        connection.commit()
        result
      } catch {
        case NonFatal(t) =>
          debug("about to rollback", List("throwable" -> Exceptions.getRootThrowable(t).toString))
          connection.rollback()
          throw t
      }
    }

  /**
    * For cases where we can't use `setString` on a prepared statement
    * - Apache Commons Lang had a `StringEscapeUtils.escapeSql` [1] but it has been deprecated in Commons Lang 3 [2]
    * - just escaping the quote is enough for safety but might return the wrong result if the string is in a
    *   LIKE; in that case more characters should be escaped [3]
    *
    * [1]: http://javasourcecode.org/html/open-source/commons-lang/commons-lang-2.6/org/apache/commons/lang/StringEscapeUtils.java.html
    * [2]: http://commons.apache.org/proper/commons-lang/article3_0.html
    * [3]: http://www.jguru.com/faq/view.jsp?EID=8881
    */
  def sqlString(text: String): String = "'" + text.replace("'", "''") + "'"

  def getIntOpt(resultSet: ResultSet, columnLabel: String): Option[Int] = {
    val readInt = resultSet.getInt(columnLabel)
    val valid   = ! resultSet.wasNull()
    valid.option(readInt)
  }

  private def getDataSourceNameFromHeaders =
    NetUtils.getExternalContext.getRequest.getFirstHeaderIgnoreCase("orbeon-datasource") getOrElse
      (throw new OXFException("Missing `orbeon-datasource` header"))

  private def getDataSource(name: String): DataSource =
    withDebug(s"getting datasource `$name`") {
      val jdbcContext: Context = InitialContext.doLookup("java:comp/env/jdbc")
      jdbcContext.lookup(name).asInstanceOf[DataSource]
    }

  def getConnection(dataSource: DataSource): Connection =
    withDebug("getting connection and setting auto commit to `false`") {
      val connection = dataSource.getConnection
      try {
        connection.setAutoCommit(false)
      } catch {
        case NonFatal(t) =>
          connection.close()
          throw t
      }
      connection
    }
}
