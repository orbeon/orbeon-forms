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
package org.orbeon.oxf.fr.persistence.relational.index.status

// Case classes for information what we store in the session

sealed trait Status
case object  Stopped                                  extends Status
case class   Starting (providers     : List[String])  extends Status
case object  Stopping                                 extends Status
case class   Indexing (provider      : String,
                       providerCount : Count,
                       documentCount : Option[Count]) extends Status

case class   Count    (current       : Int,
                       total         : Int)
