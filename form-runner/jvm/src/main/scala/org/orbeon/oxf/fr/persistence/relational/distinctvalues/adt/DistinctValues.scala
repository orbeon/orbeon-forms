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


sealed trait Values { def distinctValues: Seq[String] }

case class ControlValues (path: String,       distinctValues: Seq[String]) extends Values
case class MetadataValues(metadata: Metadata, distinctValues: Seq[String]) extends Values

case class DistinctValues(values: Seq[Values] = Seq.empty)

sealed trait Metadata {
  def string   : String
  def sqlColumn: String
}

object Metadata {
  case object CreatedBy      extends Metadata {
    override val string    = "created-by"
    override val sqlColumn = "username"
  }
  case object LastModifiedBy extends Metadata {
    override val string    = "last-modified-by"
    override val sqlColumn = "last_modified_by"
  }
  case object WorkflowStage  extends Metadata {
    override val string    = "workflow-stage"
    override val sqlColumn = "stage"
  }

  val values: Seq[Metadata] = Seq(CreatedBy, LastModifiedBy, WorkflowStage)

  def apply(string: String): Metadata =
    values.find(_.string == string).getOrElse(throw new IllegalArgumentException(s"Invalid metadata: $string"))
}
