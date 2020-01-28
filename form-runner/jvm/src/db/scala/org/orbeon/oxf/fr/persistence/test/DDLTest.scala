/**
 * Copyright (C) 2019 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.test

import org.junit.Test
import org.orbeon.oxf.fr.persistence.db._
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.Provider._
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.io.IOUtils._
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory, Logging}
import org.scalatestplus.junit.AssertionsForJUnit

/**
 * Test the DDL we provide to create and update databases.
 */
class DDLTest extends ResourceManagerTestBase with AssertionsForJUnit with Logging {

  private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[DDLTest]), true)

  case class TableMeta(tableName: String, colsMeta: Seq[ColMeta])
  case class ColMeta(colName: String, meta: Set[ColKeyVal])
  case class ColKeyVal(key: String, value: AnyRef)

  /**
   * Runs the SQL, and returns the information about the tables as defined in the database. The form in which this
   * information is returned varies depending on the database, hence the Any return type.
   */
  private def sqlToTableInfo(provider: Provider, sql: Seq[String]): List[TableMeta] = {
    Connect.withNewDatabase(provider) { connection =>
      val statement = connection.createStatement
      SQL.executeStatements(provider, statement, sql)
      val query = provider match {
        // On Oracle
        // - Column order is "non-relevant", so we order by column name instead of position
        // - We don't test on the owner with `= user` because the user is still `sys` after
        //   `ALTER SESSION SET CURRENT_SCHEMA = c##orbeon`
        case MySQL =>
          """   SELECT *
            |     FROM information_schema.columns
            |    WHERE table_name = ?
            |          AND table_schema = DATABASE()
            | ORDER BY ordinal_position"""
      }
      Connect.getTableNames(provider, connection).map { tableName =>
        useAndClose(connection.prepareStatement(query.stripMargin)) { ps =>
          ps.setString(1, tableName)
          useAndClose(ps.executeQuery()) { tableInfoResultSet =>
            def tableInfo(): ColMeta = {
              val colName = tableInfoResultSet.getString("column_name")
              val interestingKeys = Set("is_nullable", "data_type")
              val colKeyVals = for (metaKey <- interestingKeys) yield
                ColKeyVal(metaKey, tableInfoResultSet.getObject(metaKey))
              ColMeta(colName, colKeyVals)
            }
            val colsMeta = Iterator.iterateWhile(tableInfoResultSet.next(), tableInfo()).toList.sortBy(_.colName)
            assert(colsMeta.nonEmpty)
            TableMeta(tableName, colsMeta)
          }
        }
      }
    }
  }

  private def assertSameTable(provider: Provider, from: String, to: String): Unit = {
    val name = provider.entryName
    withDebug("comparing upgrade to straight", List("provider" -> name, "from" -> from, "to" -> to)) {
      val upgrade  = sqlToTableInfo(provider, SQL.read(s"$name-$from.sql") ++ SQL.read(s"$name-$from-to-$to.sql"))
      val straight = sqlToTableInfo(provider, SQL.read(s"$name-$to.sql"))
      assert(upgrade === straight, s"$name from $from to $to")
    }
  }

  @Test def createAndUpgradeTest(): Unit = {
    Connect.ProvidersTestedAutomatically.foreach {
      case provider @ MySQL =>
        assertSameTable(provider, "4_3"    , "4_4")
        assertSameTable(provider, "4_4"    , "4_5")
        assertSameTable(provider, "4_5"    , "4_6")
        assertSameTable(provider, "4_5"    , "4_6")
        assertSameTable(provider, "4_6"    , "2016_2")
        assertSameTable(provider, "2016_2" , "2016_3")
      case provider @ PostgreSQL =>
        assertSameTable(provider, "4_8"    , "2016_2")
        assertSameTable(provider, "2016_2" , "2016_3")
        assertSameTable(provider, "2016_3" , "2017_2")
        assertSameTable(provider, "2017_2" , "2018_2")
        assertSameTable(provider, "2018_2" , "2019_1")
    }
  }
}
