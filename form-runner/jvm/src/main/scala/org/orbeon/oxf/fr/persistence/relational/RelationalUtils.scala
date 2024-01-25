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

import org.log4s
import org.orbeon.errorified.Exceptions
import org.orbeon.io.IOUtils._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.fr.FormRunner.providerPropertyAsBoolean
import org.orbeon.oxf.fr.FormRunnerPersistence.PersistencePropertyPrefix
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.processor.DatabaseContext
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, DateUtilsUsingSaxon, IndentedLogger, LoggerFactory, Logging, NetUtils}

import java.sql.{Connection, ResultSet}
import java.time.Instant
import javax.naming.InitialContext
import javax.sql.DataSource
import scala.collection.compat._
import scala.util.control.NonFatal
import scala.util.{Success, Try}


object RelationalUtils extends Logging {

  val Logger: log4s.Logger = LoggerFactory.createLogger("org.orbeon.relational")

  def newIndentedLogger: IndentedLogger =
    new IndentedLogger(Logger)

  def databaseConfigurationPresent(implicit indentedLogger: IndentedLogger): Boolean = {
    val propertySet = Properties.instance.getPropertySet

    val propertiesByProvider = propertySet
      .propertiesStartsWith("oxf.fr.persistence.provider", matchWildcards = false)
      .flatMap(p => propertySet.getNonBlankString(p).map(_ -> p))
      .filter(_._1 != "resource")
      .groupBy(_._1)
      .map(kv => kv._1 -> kv._2.map(_._2).sorted)

    val providers = propertiesByProvider.keys.toSet

    val activeDataSources = providers.filter(providerPropertyAsBoolean(_, "active", default = true))

    val problematicDataSources = activeDataSources.filter { provider =>
      val dataSourceName   = propertySet.getNonBlankString(s"$PersistencePropertyPrefix.$provider.datasource")

      dataSourceName match {
        case Some(name) =>
          val dataSourceNotFound = getDataSourceNoFallback(name).isEmpty
          if (dataSourceNotFound) {
            error(s"Data source `$name` referenced by provider `$provider` not configured")

            val propertiesWithProvider = propertiesByProvider.get(provider).toSeq.flatten

            error(s"Provider `$provider` is used by the following properties:")
            propertiesWithProvider.foreach(property => error(s" - $property"))

            val activeProperty = PersistencePropertyPrefix :: provider :: "active" :: Nil mkString "."
            error(s"Provider `$provider` can be disabled by setting property `$activeProperty` to `false`")
          }
          dataSourceNotFound
        case None =>
          error(s"No data source configured for provider `$provider`")
          true
      }
    }

    problematicDataSources.isEmpty
  }

  def withConnection[T](thunk: Connection => T)(implicit indentedLogger: IndentedLogger): T =
    withConnection(getDataSourceNameFromHeaders)(thunk)

  def withConnection[T](datasourceName: String)(thunk: Connection => T)(implicit indentedLogger: IndentedLogger): T =
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

  private def getDataSourceNoFallback(name: String): Option[DataSource] = {
    val prefixesToTry = Seq("java:comp/env/jdbc/", "java:/jdbc/")

    // Workaround for WildFly (TODO: do we really need it?)
    prefixesToTry
      .toStream
      .map(prefix => Try(InitialContext.doLookup(prefix + name).asInstanceOf[DataSource]))
      .collectFirst { case Success(dataSource) => dataSource }
  }

  private def getDataSource(name: String)(implicit indentedLogger: IndentedLogger): DataSource =
    withDebug(s"getting datasource `$name`") {
      getDataSourceNoFallback(name).getOrElse(DatabaseContext.fallbackDataSource(CoreCrossPlatformSupport.externalContext, name))
    }

  def getConnection(dataSource: DataSource)(implicit indentedLogger: IndentedLogger): Connection =
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

  def parsePositiveIntParamOrThrow(paramValue: Option[String], default: Int): Int =
    paramValue
      .flatMap(_.trimAllToOpt)
      .map(_.toIntOption).map{
        case Some(v) if v <= 0 => throw HttpStatusCodeException(StatusCode.BadRequest)
        case None              => throw HttpStatusCodeException(StatusCode.BadRequest)
        case Some(v)           => v
    }.getOrElse(default)


  def instantFromString(string: String): Instant =
    Try {
      Instant.parse(string)
    } orElse Try {
      Instant.ofEpochMilli(DateUtilsUsingSaxon.parseISODateOrDateTime(string))
    } getOrElse {
      throw new IllegalArgumentException(s"Invalid date/time format: $string")
    }
}
