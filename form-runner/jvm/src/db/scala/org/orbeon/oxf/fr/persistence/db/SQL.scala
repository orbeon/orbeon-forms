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
package org.orbeon.oxf.fr.persistence.db

import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.SqlReader
import org.orbeon.oxf.util.{IndentedLogger, Logging}

import java.sql.Statement


private[persistence] object SQL extends Logging {

  private val Base = "oxf:/apps/fr/persistence/relational/ddl/"

  // Reads a sequence semicolon-separated of statements from a text file
  def executeStatements(provider: Provider, statement: Statement, sql: Seq[String])(implicit logger: IndentedLogger): Unit =
    withDebug("running statements", List("provider" -> provider.entryName)) {
      sql foreach { s =>
        withDebug("running", List("statement" -> s)) {
          statement.executeUpdate(s)
        }
      }
    }
}
