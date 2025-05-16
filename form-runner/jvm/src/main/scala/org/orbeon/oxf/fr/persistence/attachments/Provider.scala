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
package org.orbeon.oxf.fr.persistence.attachments

import enumeratum.EnumEntry.Lowercase
import enumeratum.*

sealed trait Provider extends EnumEntry with Lowercase with CRUDMethods

object Provider extends Enum[Provider] {
  val values = findValues

  case object Filesystem extends Provider with FilesystemCRUD
  case object S3         extends Provider with S3CRUD
}
