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

import org.orbeon.oxf.fr.persistence.relational.form.adt.Metadata.StringOption


sealed trait MatchType {
  def string: String
  def satisfies[T](formValue: T, queryValue: T, metadata: Metadata[?]): Boolean

  protected def throwInvalidType(metadata: Metadata[?]): Nothing =
    throw new IllegalArgumentException(s"Match type $string not supported for metadata ${metadata.string}")
}

object MatchType {
  trait ComparisonMatchType extends MatchType {
    override def satisfies[T](formValue: T, queryValue: T, metadata: Metadata[?]): Boolean =
      satisfies(Order.compare(formValue, queryValue))

    def satisfies(compareValue: Int): Boolean
  }

  object GreaterThanOrEqual extends ComparisonMatchType {
    override val string = "gte"

    override def satisfies(compareValue: Int): Boolean = compareValue >= 0
  }

  object GreaterThan extends ComparisonMatchType {
    override val string = "gt"

    override def satisfies(compareValue: Int): Boolean = compareValue > 0
  }

  object LowerThanOrEqual extends ComparisonMatchType {
    override val string = "lte"

    override def satisfies(compareValue: Int): Boolean = compareValue <= 0
  }

  object LowerThan extends ComparisonMatchType {
    override val string = "lt"

    override def satisfies(compareValue: Int): Boolean = compareValue < 0
  }

  object Exact extends ComparisonMatchType {
    override val string = "exact"

    override def satisfies(compareValue: Int): Boolean = compareValue == 0
  }

  object Substring extends MatchType {
    override val string = "substring"

    override def satisfies[T](formValue: T, queryValue: T, metadata: Metadata[?]): Boolean =
      (formValue, queryValue) match {
        case (formValue: String, queryValue: String) =>
          formValue.toLowerCase.contains(queryValue.toLowerCase)

        case (formValue: StringOption, queryValue: StringOption) =>
          (for {
            formString  <- formValue.stringOpt
            queryString <- queryValue.stringOpt
          } yield formString.toLowerCase.contains(queryString.toLowerCase)).getOrElse(false)

        case _ => throwInvalidType(metadata)
      }
  }

  object Token extends MatchType {
    override val string = "token"

    override def satisfies[T](formValue: T, queryValue: T, metadata: Metadata[?]): Boolean =
      (formValue, queryValue) match {
        case (formValues: OperationsList, queryValues: OperationsList) =>
          // e.g. "admin *", "admin create", or "update delete list"
          val formValueSet = formValues.ops.map(_.toLowerCase).toSet

          // All operations can be matched with a wildcard except "admin"
          val operationsThatCanMatchWildcard = Set("create", "read", "update", "delete", "list")

          queryValues.ops.map(_.toLowerCase).forall { queryValue =>
            if (operationsThatCanMatchWildcard.contains(queryValue))
              formValueSet.contains("*") || formValueSet.contains(queryValue)
            else
              formValueSet.contains(queryValue)
          }

        case _ =>
          throwInvalidType(metadata)
      }
  }

  val values: Seq[MatchType] = Seq(GreaterThanOrEqual, GreaterThan, LowerThanOrEqual, LowerThan, Exact, Substring, Token)

  def apply(string: String): MatchType =
    values.find(_.string == string.toLowerCase).getOrElse(throw new IllegalArgumentException(s"Invalid match type: $string"))
}
