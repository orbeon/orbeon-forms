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
package org.orbeon.oxf.fr.persistence

package object relational {

  sealed trait Provider    extends Product with Serializable { val token: String }

  case object  Oracle      extends Provider { val token = "oracle"     }
  case object  MySQL       extends Provider { val token = "mysql"      }
  case object  SQLServer   extends Provider { val token = "sqlserver"  }
  case object  PostgreSQL  extends Provider { val token = "postgresql" }
  case object  DB2         extends Provider { val token = "db2"        }

  val AllProviders = List(Oracle, MySQL, SQLServer, PostgreSQL, DB2)

}
