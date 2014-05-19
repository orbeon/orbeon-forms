/**
 * Copyright (C) 2014 Orbeon, Inc.
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

private[persistence] sealed abstract class Provider(val name: String)

object Provider {
    def all: List[Provider] = List(MySQL, SQLServer)
}

private[persistence] case object Oracle    extends Provider("oracle")
private[persistence] case object MySQL     extends Provider("mysql")
private[persistence] case object SQLServer extends Provider("sqlserver")
private[persistence] case object DB2       extends Provider("db2")
