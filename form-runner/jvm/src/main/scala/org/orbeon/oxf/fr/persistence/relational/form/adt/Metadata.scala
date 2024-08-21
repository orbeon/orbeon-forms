/**
 * Copyright (C) 2024 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.form.adt

import cats.implicits.catsSyntaxOptionId
import org.orbeon.oxf.fr.FormDefinitionVersion
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps

import java.time.Instant


sealed trait Metadata[T] {
  def string     : String
  def sqlColumn  : String
  def supportsUrl: Boolean

  def valueFromString(string: String): T
  def valueAsString(value: T): String

  def valueOpt(form: Form, formMetadataOpt: Option[FormMetadata], language: => String): Option[T]

  def allowedMatchTypes: Set[MatchType]
}

object Metadata {

  import MatchType.*

  trait BooleanMetadata extends Metadata[Boolean] {
    override def valueFromString(string: String): Boolean = string.toBoolean
    override def valueAsString(value: Boolean): String    = value.toString

    override val allowedMatchTypes: Set[MatchType] = Set(Exact)
  }

  trait FormDefinitionVersionMetadata extends Metadata[FormDefinitionVersion] {
    override def valueFromString(string: String): FormDefinitionVersion = string match {
      case FormDefinitionVersion.Latest.string => FormDefinitionVersion.Latest
      case _                                   => FormDefinitionVersion.Specific(string.toInt)
    }
    override def valueAsString(value: FormDefinitionVersion): String = value match {
      case FormDefinitionVersion.Latest            => FormDefinitionVersion.Latest.string
      case FormDefinitionVersion.Specific(version) => version.toString
    }

    override val allowedMatchTypes: Set[MatchType] = Set(GreaterThanOrEqual, GreaterThan, LowerThan, Exact)
  }

  trait InstantMetadata extends Metadata[Instant] {
    override def valueFromString(string: String): Instant = Instant.parse(string)
    override def valueAsString(value : Instant): String   = value.toString

    override val allowedMatchTypes: Set[MatchType] = Set(GreaterThanOrEqual, GreaterThan, LowerThan, Exact)
  }

  trait StringMetadata extends Metadata[String] {
    override def valueFromString(string: String): String = string
    override def valueAsString(value: String): String    = value

    override val allowedMatchTypes: Set[MatchType] = Set(Exact, Substring)
  }

  trait StringListMetadata extends Metadata[List[String]] {
    override def valueFromString(string: String): List[String] = string.splitTo[List]()
    override def valueAsString(value: List[String]): String    = value.mkString(" ")

    override val allowedMatchTypes: Set[MatchType] = Set(Exact, Token)
  }

  case object AppName        extends StringMetadata {
    override val string      = "application-name"
    override val sqlColumn   = "app"
    override val supportsUrl = false

    // Only allow Exact queries for now
    override val allowedMatchTypes: Set[MatchType] = Set(Exact)

    override def valueOpt(form: Form, formMetadataOpt: Option[FormMetadata], language: => String): Option[String] =
      form.appForm.app.some
  }

  case object FormName       extends StringMetadata {
    override val string      = "form-name"
    override val sqlColumn   = "form"
    override val supportsUrl = false

    // Only allow Exact queries for now
    override val allowedMatchTypes: Set[MatchType] = Set(Exact)

    override def valueOpt(form: Form, formMetadataOpt: Option[FormMetadata], language: => String): Option[String] =
      form.appForm.form.some
  }

  case object FormVersion    extends FormDefinitionVersionMetadata {
    override val string      = "form-version"
    override val sqlColumn   = "form_version"
    override val supportsUrl = false

    override def valueOpt(form: Form, formMetadataOpt: Option[FormMetadata], language: => String): Option[FormDefinitionVersion] =
      form.version.some
  }

  case object Created        extends InstantMetadata {
    override val string      = "created"
    override val sqlColumn   = "created"
    override val supportsUrl = true

    override def valueOpt(form: Form, formMetadataOpt: Option[FormMetadata], language: => String): Option[Instant] =
      formMetadataOpt.map(_.created)
  }

  case object LastModified   extends InstantMetadata {
    override val string      = "last-modified"
    override val sqlColumn   = "last_modified_time"
    override val supportsUrl = true

    override def valueOpt(form: Form, formMetadataOpt: Option[FormMetadata], language: => String): Option[Instant] =
      formMetadataOpt.map(_.lastModifiedTime)
  }

  case object LastModifiedBy extends StringMetadata {
    override val string      = "last-modified-by"
    override val sqlColumn   = "last_modified_by"
    override val supportsUrl = true

    override def valueOpt(form: Form, formMetadataOpt: Option[FormMetadata], language: => String): Option[String] =
      formMetadataOpt.flatMap(_.lastModifiedByOpt)
  }

  case object Title          extends StringMetadata {
    override val string      = "title"
    override val sqlColumn   = "form_metadata"
    override val supportsUrl = true

    override def valueOpt(form: Form, formMetadataOpt: Option[FormMetadata], language: => String): Option[String] =
      formMetadataOpt.flatMap(_.title.get(language))
  }

  case object Available      extends BooleanMetadata {
    override val string      = "available"
    override val sqlColumn   = "form_metadata"
    override val supportsUrl = true

    override def valueOpt(form: Form, formMetadataOpt: Option[FormMetadata], language: => String): Option[Boolean] =
      formMetadataOpt.map(_.available)
  }

  case object Operations     extends StringListMetadata {
    override val string      = "operations"
    override val sqlColumn   = "form_metadata"
    override val supportsUrl = true

    override def valueOpt(form: Form, formMetadataOpt: Option[FormMetadata], language: => String): Option[List[String]] =
      formMetadataOpt.map(_.operations)
  }

  val values: Seq[Metadata[?]] = Seq(AppName, FormName, FormVersion, Created, LastModified, LastModifiedBy, Title, Available, Operations)

  def apply(string: String): Metadata[?] =
    values.find(_.string == string).getOrElse(throw new IllegalArgumentException(s"Invalid metadata: $string"))
}
