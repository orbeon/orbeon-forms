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
import org.orbeon.oxf.fr.FormRunner.getDefaultLang
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.fr.persistence.relational.form.adt.LocalRemoteOrCombinator.*
import org.orbeon.oxf.util.StringUtils.*

import java.time.Instant


sealed trait Metadata[T] {
  def string   : String
  def sqlColumn: String

  def valueFromString(string: String): T
  def valueAsString(value: T): String

  def allowedMatchTypes: Set[MatchType]

  def valueOpt(
    form                   : Form,
    localRemoteOrCombinator: LocalRemoteOrCombinator,
    languageOpt            : => Option[String]
  ): Option[T]

  protected def throwInvalidUrlOrCombinator(localRemoteOrCombinator: LocalRemoteOrCombinator): Nothing =
    throw new IllegalArgumentException(s"Invalid URL/combinator ${localRemoteOrCombinator.string} for metadata $string")
}

object Metadata {

  import MatchType.*

  trait MultiValueSupport[T] {
    this: Metadata[T] =>

    def valueOpt(
      form                   : Form,
      localRemoteOrCombinator: LocalRemoteOrCombinator,
      languageOpt            : => Option[String]
    ): Option[T] = {

      lazy val language = languageOpt.getOrElse(getDefaultLang(form.appForm.some))

      def allValues = (form.localMetadataOpt.toList ++ form.remoteMetadata.values).map(value(_, language))

      localRemoteOrCombinator match {
        case Local       => form.localMetadataOpt.map(value(_, language))
        case Remote(url) => form.remoteMetadata.get(url).map(value(_, language))
        case Min         => min(allValues)
        case Max         => max(allValues)
        case Or          => or (allValues)
        case And         => and(allValues)
      }
    }

    def value(formMetadata: FormMetadata, language: => String): T

    // Unsupported by default
    def min(values: List[T]): Option[T] = throwInvalidUrlOrCombinator(Min)
    def max(values: List[T]): Option[T] = throwInvalidUrlOrCombinator(Max)
    def or (values: List[T]): Option[T] = throwInvalidUrlOrCombinator(Or)
    def and(values: List[T]): Option[T] = throwInvalidUrlOrCombinator(And)
  }

  trait BooleanMetadata extends Metadata[Boolean] with MultiValueSupport[Boolean] {
    override def valueFromString(string: String): Boolean = string.toBoolean
    override def valueAsString(value: Boolean): String    = value.toString

    override val allowedMatchTypes: Set[MatchType] = Set(Exact)

    override def or(values: List[Boolean]): Option[Boolean] =
      values match {
        case Nil => None
        case _   => values.reduce(_ || _).some
      }

    override def and(values: List[Boolean]): Option[Boolean] =
      values match {
        case Nil => None
        case _   => values.reduce(_ && _).some
      }
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

    override val allowedMatchTypes: Set[MatchType] = Set(GreaterThanOrEqual, GreaterThan, LowerThanOrEqual, LowerThan, Exact)
  }

  trait InstantMetadata extends Metadata[Instant] with MultiValueSupport[Instant] {
    override def valueFromString(string: String): Instant = RelationalUtils.instantFromString(string)
    override def valueAsString(value : Instant): String   = value.toString

    override val allowedMatchTypes: Set[MatchType] = Set(GreaterThanOrEqual, GreaterThan, LowerThanOrEqual, LowerThan, Exact)

    override def min(values: List[Instant]): Option[Instant] =
      values match {
        case Nil => None
        case _   => values.min.some
      }

    override def max(values: List[Instant]): Option[Instant] =
      values match {
        case Nil => None
        case _   => values.max.some
      }
  }

  trait StringMetadata extends Metadata[String] {
    override def valueFromString(string: String): String = string
    override def valueAsString(value: String): String    = value

    override val allowedMatchTypes: Set[MatchType] = Set(Exact, Substring)
  }

  // For pattern matching
  case class StringOption(stringOpt: Option[String])
  case class InstantOption(instantOpt: Option[Instant])

  trait StringOptionMetadata extends Metadata[StringOption] with MultiValueSupport[StringOption] {
    override def valueFromString(string: String): StringOption = StringOption(Some(string).filter(_.nonEmpty))
    override def valueAsString(value: StringOption): String    = value.stringOpt.getOrElse("")

    override val allowedMatchTypes: Set[MatchType] = Set(Exact, Substring)
  }

  trait InstantOptionMetadata extends Metadata[InstantOption] with MultiValueSupport[InstantOption] {
    override def valueFromString(string: String): InstantOption = InstantOption(Some(string).filter(_.nonEmpty).map(RelationalUtils.instantFromString))
    override def valueAsString(value: InstantOption): String    = value.instantOpt.map(_.toString).getOrElse("")

    override val allowedMatchTypes: Set[MatchType] = Set(GreaterThanOrEqual, GreaterThan, LowerThanOrEqual, LowerThan, Exact)

    override def min(values: List[InstantOption]): Option[InstantOption] =
      values.flatMap(_.instantOpt) match {
        case Nil => None
        case instants => InstantOption(instants.min.some).some
      }

    override def max(values: List[InstantOption]): Option[InstantOption] =
      values.flatMap(_.instantOpt) match {
        case Nil => None
        case instants => InstantOption(instants.max.some).some
      }
  }

  trait OperationsListMetadata extends Metadata[OperationsList] with MultiValueSupport[OperationsList] {
    override def valueFromString(string: String): OperationsList = OperationsList(string.splitTo[List]())
    override def valueAsString(value: OperationsList): String    = value.ops.mkString(" ")

    override val allowedMatchTypes: Set[MatchType] = Set(Exact, Token)

    // Operations union
    override def or(values: List[OperationsList]): Option[OperationsList] =
      values match {
        case Nil => None
        case _   => OperationsList(values.flatMap(_.ops).distinct).some
      }

    // Operations intersection
    override def and(values: List[OperationsList]): Option[OperationsList] =
      values match {
        case Nil => None
        case _   => OperationsList(values.map(_.ops).reduce(_.intersect(_))).some
      }
  }

  case object AppName extends StringMetadata {
    override val string    = "application-name"
    override val sqlColumn = "app"

    override def valueOpt(
      form                   : Form,
      localRemoteOrCombinator: LocalRemoteOrCombinator,
      languageOpt            : => Option[String]
    ): Option[String] =
      if (localRemoteOrCombinator == Local) form.appForm.app.some else throwInvalidUrlOrCombinator(localRemoteOrCombinator)
  }

  case object FormName extends StringMetadata {
    override val string    = "form-name"
    override val sqlColumn = "form"

    override def valueOpt(
      form                   : Form,
      localRemoteOrCombinator: LocalRemoteOrCombinator,
      languageOpt            : => Option[String]
    ): Option[String] =
      if (localRemoteOrCombinator == Local) form.appForm.form.some else throwInvalidUrlOrCombinator(localRemoteOrCombinator)
  }

  case object FormVersion extends FormDefinitionVersionMetadata {
    override val string    = "form-version"
    override val sqlColumn = "form_version"

    override def valueOpt(
      form                   : Form,
      localRemoteOrCombinator: LocalRemoteOrCombinator,
      languageOpt            : => Option[String]
    ): Option[FormDefinitionVersion] =
      if (localRemoteOrCombinator == Local) form.version.some else throwInvalidUrlOrCombinator(localRemoteOrCombinator)
  }

  case object Created extends InstantOptionMetadata {
    override val string    = "created"
    override val sqlColumn = "created"

    override def value(formMetadata: FormMetadata, language: => String): InstantOption =
      InstantOption(formMetadata.created)
  }

  case object LastModified extends InstantMetadata {
    override val string    = "last-modified"
    override val sqlColumn = "last_modified_time"

    override def value(formMetadata: FormMetadata, language: => String): Instant =
      formMetadata.lastModifiedTime
  }

  case object LastModifiedBy extends StringOptionMetadata {
    override val string    = "last-modified-by"
    override val sqlColumn = "last_modified_by"

    override def value(formMetadata: FormMetadata, language: => String): StringOption =
      StringOption(formMetadata.lastModifiedByOpt)
  }

  case object Title extends StringOptionMetadata {
    override val string    = "title"
    override val sqlColumn = "form_metadata"

    override def value(formMetadata: FormMetadata, language: => String): StringOption =
      StringOption(formMetadata.title.get(language))
  }

  case object Available extends BooleanMetadata {
    override val string    = "available"
    override val sqlColumn = "form_metadata"

    override def value(formMetadata: FormMetadata, language: => String): Boolean =
      formMetadata.available
  }

  case object Operations extends OperationsListMetadata {
    override val string    = "operations"
    override val sqlColumn = "form_metadata"

    override def value(formMetadata: FormMetadata, language: => String): OperationsList =
      formMetadata.operations
  }

  val values: Seq[Metadata[?]] = Seq(AppName, FormName, FormVersion, Created, LastModified, LastModifiedBy, Title, Available, Operations)

  def apply(string: String): Metadata[?] =
    values.find(_.string == string.toLowerCase).getOrElse(throw new IllegalArgumentException(s"Invalid metadata: $string"))
}
