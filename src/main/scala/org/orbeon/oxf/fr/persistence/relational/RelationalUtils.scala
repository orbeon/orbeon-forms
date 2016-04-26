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

import java.sql.Connection
import javax.naming.{Context, InitialContext}
import javax.sql.DataSource

import org.orbeon.errorified.Exceptions
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContextOps._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory, Logging, NetUtils}

import scala.util.control.NonFatal

object RelationalUtils extends Logging {

  implicit val Logger = new IndentedLogger(LoggerFactory.createLogger("org.orbeon.relational"))

  def withConnection[T](thunk: Connection ⇒ T): T =
    useAndClose(getConnection(getDataSource(getDataSourceNameFromHeaders))) { connection ⇒
      try {
        val result = withDebug("executing block with connection")(thunk(connection))
        debug("about to commit")
        connection.commit()
        result
      } catch {
        case NonFatal(t) ⇒
          debug("about to rollback", List("throwable" → Exceptions.getRootThrowable(t).toString))
          connection.rollback()
          throw t
      }
    }

  def xmlCol(provider: Provider, tableName: String) =
    provider match {
      case Oracle     ⇒ s"$tableName.xml.getClobVal() xml"
      case DB2        ⇒ s"xml2clob($tableName.xml) xml"
      case PostgreSQL ⇒ s"$tableName.xml as xml"
      case _          ⇒ s"$tableName.xml xml"
    }

  /**
    * For cases where we can't use `setString` on a prepared statement
    * - Apache Commons Lang had a `StringEscapeUtils.escapeSql` [1] but it has been deprecated in Commons Lang 3 [2]
    * - just escaping the quote is enough for safety but might return the wrong result if the string is in a
    * LIKE; in that case more characters should be escaped [3]
    *
    * [1]: http://javasourcecode.org/html/open-source/commons-lang/commons-lang-2.6/org/apache/commons/lang/StringEscapeUtils.java.html
    * [2]: http://commons.apache.org/proper/commons-lang/article3_0.html
    * [3]: http://www.jguru.com/faq/view.jsp?EID=8881
    */
  def sqlString(text: String) = "'" + text.replaceAllLiterally("'", "''") + "'"

  private def getDataSourceNameFromHeaders =
    NetUtils.getExternalContext.getRequest.getFirstHeader("orbeon-datasource") getOrElse
      (throw new OXFException("Missing `orbeon-datasource` header"))

  private def getDataSource(name: String) =
    withDebug(s"getting datasource `$name`") {
      val jndiContext = new InitialContext().lookup("java:comp/env/jdbc").asInstanceOf[Context]
      jndiContext.lookup(name).asInstanceOf[DataSource]
    }

  def getConnection(dataSource: DataSource) =
    withDebug("getting connection and setting auto commit to `false`") {
      val connection = dataSource.getConnection
      try {
        connection.setAutoCommit(false)
      } catch {
        case NonFatal(t) ⇒
          connection.close()
          throw t
      }
      connection
    }
}
