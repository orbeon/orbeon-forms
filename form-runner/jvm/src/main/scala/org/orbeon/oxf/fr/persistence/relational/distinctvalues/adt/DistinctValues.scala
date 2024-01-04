/**
 * Copyright (C) 2023 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.distinctvalues.adt

case class ControlValues(path: String, distinctValues: Seq[String])

case class DistinctValues(
  controlValues       : Seq[ControlValues] = Seq.empty,
  createdByValues     : Option[Seq[String]] = None,
  lastModifiedByValues: Option[Seq[String]] = None,
  workflowStageValues : Option[Seq[String]] = None
)
