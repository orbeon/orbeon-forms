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
import org.orbeon.oxf.fr.persistence.relational.form.adt.LocalRemoteOrCombinator.Local
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.NodeInfoOps

import java.time.Instant


case class FilterQuery[T](
  metadata               : Metadata[T],
  languageOpt            : Option[String],
  localRemoteOrCombinator: LocalRemoteOrCombinator,
  matchType              : MatchType,
  value                  : T,
  exclude                : Boolean = false
) extends FilterOrSortQuery[T] {

  def toXML: NodeInfo =
    elem(
      "filter",
      metadata.valueAsString(value),
      (Seq("match" -> matchType.string) ++ (if (exclude) Seq("exclude" -> "true") else Nil)) *
    )

  def satisfies(form: Form, formRequest: FormRequest): Boolean = {
    val matches =
      metadata.valueOpt(form, localRemoteOrCombinator, effectiveLanguageOpt(formRequest)) match {
        case None            => false // No form value was found (e.g. query on local metadata, but only remote metadata is available)
        case Some(formValue) => matchType.satisfies(formValue, value, metadata)
      }
    if (exclude) ! matches else matches
  }
}

object FilterQuery {
  def apply[T](xml: NodeInfo): FilterQuery[T] = {

    // TODO: fix type design/inference
    val metadata: Metadata[T] = Metadata(xml.attValue("metadata")).asInstanceOf[Metadata[T]]
    val matchType             = MatchType(xml.attValue("match"))
    val exclude               =
      xml.attValueOpt("exclude").map(_.toLowerCase) match {
        case None          => false
        case Some("true")  => true
        case Some("false") => false
        case Some(other)   => throw new IllegalArgumentException(s"Invalid `exclude` attribute value: `$other`")
      }

    if (! metadata.allowedMatchTypes.contains(matchType)) {
      throw new IllegalArgumentException(s"Match type `${matchType.string}` is not allowed for metadata `${metadata.string}`")
    }

    FilterQuery(
      metadata                = metadata,
      languageOpt             = xml.attValueOpt("*:lang"),
      localRemoteOrCombinator = LocalRemoteOrCombinator(xml),
      matchType               = matchType,
      value                   = metadata.valueFromString(xml.stringValue),
      exclude                 = exclude
    )
  }

  def exactAppQuery(app: String): FilterQuery[String] =
    FilterQuery(
      metadata                = Metadata.AppName,
      languageOpt             = None,
      localRemoteOrCombinator = Local,
      matchType               = MatchType.Exact,
      value                   = app
    )

  def excludedExactAppQuery(app: String): FilterQuery[String] =
    FilterQuery(
      metadata                = Metadata.AppName,
      languageOpt             = None,
      localRemoteOrCombinator = Local,
      matchType               = MatchType.Exact,
      value                   = app,
      exclude                 = true
    )

  def exactFormQuery(form: String): FilterQuery[String] =
    FilterQuery(
      metadata                = Metadata.FormName,
      languageOpt             = None,
      localRemoteOrCombinator = Local,
      matchType               = MatchType.Exact,
      value                   = form
    )

  val latestVersionsQuery: FilterQuery[FormDefinitionVersion] =
    FilterQuery(
      metadata                = Metadata.FormVersion,
      languageOpt             = None,
      localRemoteOrCombinator = Local,
      matchType               = MatchType.Exact,
      value                   = FormDefinitionVersion.Latest
    )

  def modifiedSinceQuery(modifiedSince: Instant): FilterQuery[Instant] =
    FilterQuery(
      metadata                = Metadata.LastModified,
      languageOpt             = None,
      localRemoteOrCombinator = Local,
      matchType               = MatchType.GreaterThan,
      value                   = modifiedSince
    )
}
