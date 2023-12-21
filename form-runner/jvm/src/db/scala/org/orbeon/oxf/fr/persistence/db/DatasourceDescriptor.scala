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
package org.orbeon.oxf.fr.persistence.db

import org.orbeon.oxf.fr.persistence.db.Connect.TestDatabaseName
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.Provider.{MySQL, PostgreSQL, SQLite}

import java.io.File

case class DatasourceDescriptor(
  name      : String,
  driver    : String,
  url       : String,
  username  : String,
  password  : String,
  switchDB  : Option[String]
)

object DatasourceDescriptor {

  def apply(provider: Provider): DatasourceDescriptor = {

    provider match {
      case MySQL =>
        DatasourceDescriptor(
          name      = provider.entryName,
          driver    = "com.mysql.cj.jdbc.Driver",
          url       = "jdbc:mysql://localhost:3306/",
          username  = "root",
          password  = "",
          switchDB  = Some(s"USE $TestDatabaseName")
        )
      case PostgreSQL =>
        DatasourceDescriptor(
          name      = provider.entryName,
          driver    = "org.postgresql.Driver",
          url       = "jdbc:postgresql://localhost:5432/",
          username  = "orbeon",
          password  = "",
          switchDB  = Some(s"SET search_path TO $TestDatabaseName")
        )
      case SQLite =>
        // SQLite file will be created in the current directory. This is fine for tests.

        val sqliteFilename = "test-db.sqlite"

        // Delete the file if it exists
        new File(sqliteFilename).delete()

        DatasourceDescriptor(
          name      = provider.entryName,
          driver    = "org.sqlite.JDBC",
          url       = s"jdbc:$sqliteFilename",
          username  = "",
          password  = "",
          switchDB  = None
    }
  }
}
