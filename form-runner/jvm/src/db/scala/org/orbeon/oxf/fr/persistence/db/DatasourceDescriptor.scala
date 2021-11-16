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

import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.Provider.{MySQL, PostgreSQL}

case class DatasourceDescriptor(
  name      : String,
  driver    : String,
  url       : String,
  username  : String,
  password  : String,
  switchDB  : String
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
          switchDB  = "USE orbeon"
        )
      case PostgreSQL =>
        DatasourceDescriptor(
          name      = provider.entryName,
          driver    = "org.postgresql.Driver",
          url       = "jdbc:postgresql://localhost:5432/",
          username  = "orbeon",
          password  = "",
          switchDB  = "SET search_path TO orbeon"
        )
    }
  }
}

