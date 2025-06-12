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
import org.orbeon.oxf.fr.persistence.relational.form.adt.Metadata.{StringOption, InstantOption}

import java.time.Instant


case object Order {
  // Option[?] ordering logic
  def compare(xOpt: Option[Any], yOpt: Option[Any]): Int = (xOpt, yOpt) match {
    case (None   , None   ) =>  0
    case (None   , Some(_)) => -1
    case (Some(_), None   ) =>  1
    case (Some(x), Some(y)) => compare(x, y)
  }

  // Non-Option[?] ordering logic
  def compare(x: Any, y: Any): Int = (x, y) match {
    case (x: Boolean, y: Boolean)                             => x.compareTo(y)
    case (x: FormDefinitionVersion, y: FormDefinitionVersion) => x.compareTo(y)
    case (x: Instant, y: Instant)                             => x.compareTo(y)
    case (x: OperationsList, y: OperationsList)               => x.ops.mkString(" ").compareTo(y.ops.mkString(" "))
    case (x: String, y: String)                               => x.toLowerCase.compareTo(y.toLowerCase) // Case-insensitive comparison
    case (x: StringOption, y: StringOption)                   => compare(x.stringOpt, y.stringOpt)
    case (x: InstantOption, y: InstantOption)                 => compare(x.instantOpt, y.instantOpt)
    case _                                                    => throw new IllegalArgumentException(s"Unsupported metadata type: ${x.getClass}")
  }

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

  def formOrdering(
    sortQuery  : SortQuery[?],
    formRequest: FormRequest
  ): Ordering[Form] = {

    val ordering = Ordering.fromLessThan[Form] { (formX, formY) =>

      val languageOpt = sortQuery.effectiveLanguageOpt(formRequest)

      val xOpt = sortQuery.metadata.valueOpt(formX, sortQuery.localRemoteOrCombinator, languageOpt)
      val yOpt = sortQuery.metadata.valueOpt(formY, sortQuery.localRemoteOrCombinator, languageOpt)

      Order.compare(xOpt, yOpt) < 0
    }

    sortQuery.orderDirection match {
      case OrderDirection.Ascending  => ordering
      case OrderDirection.Descending => ordering.reverse
    }
  }
}
