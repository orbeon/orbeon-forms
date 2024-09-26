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

import org.orbeon.oxf.fr.FormDefinitionVersion

import java.time.Instant


sealed trait MatchType {
  def string: String
  def satisfies[T](formValue: T, queryValue: T, metadata: Metadata[?]): Boolean

  protected def throwInvalidType(metadata: Metadata[?]): Nothing =
    throw new IllegalArgumentException(s"Match type $string not supported for metadata ${metadata.string}")
}

object MatchType {
  implicit class FormDefinitionVersionOps(formDefinitionVersion: FormDefinitionVersion) {
    // Incomplete implementation. The form version will always be specific. For the time being, we don't support
    // inequality tests with "latest" as a query value.
    def compareTo(other: FormDefinitionVersion): Int =
      (formDefinitionVersion, other) match {
        case (FormDefinitionVersion.Specific(version1), FormDefinitionVersion.Specific(version2)) =>
          version1.compareTo(version2)

        case (FormDefinitionVersion.Specific(_), FormDefinitionVersion.Latest) =>
          throw new IllegalArgumentException("Inequality queries are not supported with 'latest' yet")

        case _ =>
          throw new IllegalArgumentException("Unexpected form definition version")
      }
  }

  trait InequalityMatchType extends MatchType {
    override def satisfies[T](formValue: T, queryValue: T, metadata: Metadata[?]): Boolean =
      (formValue, queryValue) match {
        case (formValue: Instant, queryValue: Instant)                             => satisfies(formValue.compareTo(queryValue))
        case (formValue: FormDefinitionVersion, queryValue: FormDefinitionVersion) => satisfies(formValue.compareTo(queryValue))
        case _                                                                     => throwInvalidType(metadata)
      }

    def satisfies(compareToValue: Int): Boolean
  }

  object GreaterThanOrEqual extends InequalityMatchType {
    override val string = "gte"
    override def satisfies(compareToValue: Int): Boolean = compareToValue >= 0
  }

  object GreaterThan        extends InequalityMatchType {
    override val string = "gt"
    override def satisfies(compareToValue: Int): Boolean = compareToValue > 0
  }

  object LowerThan          extends InequalityMatchType {
    override val string = "lt"
    override def satisfies(compareToValue: Int): Boolean = compareToValue < 0
  }

  object Substring          extends MatchType {
    override val string = "substring"
    override def satisfies[T](formValue: T, queryValue: T, metadata: Metadata[?]): Boolean =
      (formValue, queryValue) match {
        case (formValue: String, queryValue: String) => formValue.toLowerCase.contains(queryValue.toLowerCase)
        case _                                       => throwInvalidType(metadata)
      }
  }

  object Exact              extends MatchType {
    override val string = "exact"
    override def satisfies[T](formValue: T, queryValue: T, metadata: Metadata[?]): Boolean =
      (formValue, queryValue) match {
        case (formValue: String, queryValue: String) => formValue.toLowerCase == queryValue.toLowerCase
        case _                                       => formValue == queryValue
      }
  }

  object Token              extends MatchType {
    override val string = "token"
    override def satisfies[T](formValue: T, queryValue: T, metadata: Metadata[?]): Boolean =
      (formValue, queryValue) match {
        case (formValues: List[String @unchecked], queryValues: List[String @unchecked]) =>
          val lowerCaseFormValues = formValues.map(_.toLowerCase)
          queryValues.forall(value => lowerCaseFormValues.contains(value.toLowerCase))

        case _ =>
          throwInvalidType(metadata)
      }
  }

  val values: Seq[MatchType] = Seq(GreaterThanOrEqual, GreaterThan, LowerThan, Substring, Exact, Token)

  def apply(string: String): MatchType =
    values.find(_.string == string).getOrElse(throw new IllegalArgumentException(s"Invalid match type: $string"))
}
