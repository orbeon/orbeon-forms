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
import org.orbeon.oxf.fr.persistence.relational.form.adt.Select.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.NodeInfoOps

import java.time.Instant

case class Query[T](
  metadata            : Metadata[T],
  languageOpt         : Option[String],
  select              : Select,
  matchTypeAndValueOpt: Option[(MatchType, T)],
  orderDirectionOpt   : Option[OrderDirection]
) {
  def toXML: NodeInfo = {

    val nodeValueOpt   = matchTypeAndValueOpt.map(_._2)                 .map(metadata.valueAsString)
    val languageAttOpt = languageOpt                                    .map(xml.Attribute(None.orNull, "xml:lang", _, xml.Null))
    val selectAttOpt   = select.some.filterNot(_ == Local).map(_.string).map(xml.Attribute(None.orNull, "select"     , _, xml.Null))
    val matchAttOpt    = matchTypeAndValueOpt.map(_._1.string)          .map(xml.Attribute(None.orNull, "match"   , _, xml.Null))
    val sortAttOpt     = orderDirectionOpt   .map(_.sql)                .map(xml.Attribute(None.orNull, "sort"    , _, xml.Null))

    val baseElem = <query metadata={metadata.string}>{nodeValueOpt.getOrElse("")}</query>

    Seq(languageAttOpt, selectAttOpt, matchAttOpt, sortAttOpt).flatten.foldLeft(baseElem)((elem, attribute) => elem % attribute)
  }

  def effectiveLanguageOpt(formRequest: FormRequest): Option[String] =
    languageOpt orElse formRequest.languageOpt

  def satisfies(form: Form, formRequest: FormRequest): Boolean =
    matchTypeAndValueOpt match {
      case None =>
        // No match type and value means this is a sort-only query
        true

      case Some((matchType, queryValue)) =>
        metadata.selectedValueOpt(form, select, effectiveLanguageOpt(formRequest)) match {
          case None            => false // No form value was found (e.g. query on local metadata, but only remote metadata is available)
          case Some(formValue) => matchType.satisfies(formValue, queryValue, metadata)
        }
    }
}

object Query {
  def apply[T](xml: NodeInfo): Query[T] = {

    // TODO: fix type design/inference
    val metadata: Metadata[T] = Metadata(xml.attValue("metadata")).asInstanceOf[Metadata[T]]
    val select                = xml.attValueOpt("select").map(Select.apply).getOrElse(Local)

    val matchTypeOpt = xml.attValueOpt("match").map(MatchType.apply)
    val nodeValueOpt = Option(xml.stringValue).filter(_.nonEmpty)

    if (nodeValueOpt.isDefined && matchTypeOpt.isEmpty) {
      throw new IllegalArgumentException(s"Query with value `${nodeValueOpt.get}` must have a match type")
    }

    val matchTypeAndValueOpt: Option[(MatchType, T)] = matchTypeOpt.map { matchType =>

      if (! metadata.allowedMatchTypes.contains(matchType)) {
        throw new IllegalArgumentException(s"Match type `${matchType.string}` is not allowed for metadata `${metadata.string}`")
      }

      (matchType, metadata.valueFromString(nodeValueOpt.getOrElse("")))
    }

    val orderDirectionOpt = xml.attValueOpt("sort").map(OrderDirection.apply)

    if (matchTypeAndValueOpt.isEmpty && orderDirectionOpt.isEmpty) {
      throw new IllegalArgumentException(s"Query must have either a match type and value or a sort direction")
    }

    Query(
      metadata             = metadata,
      languageOpt          = xml.attValueOpt("*:lang"),
      select               = select,
      matchTypeAndValueOpt = matchTypeAndValueOpt,
      orderDirectionOpt    = orderDirectionOpt
    )
  }

  def exactAppQuery(app: String): Query[String] =
    Query(
      metadata             = Metadata.AppName,
      languageOpt          = None,
      select               = Local,
      matchTypeAndValueOpt = (MatchType.Exact, app).some,
      orderDirectionOpt    = None
    )

  def exactFormQuery(form: String): Query[String] =
    Query(
      metadata             = Metadata.FormName,
      languageOpt          = None,
      select               = Local,
      matchTypeAndValueOpt = (MatchType.Exact, form).some,
      orderDirectionOpt    = None
    )

  val latestVersionsQuery: Query[FormDefinitionVersion] =
    Query(
      metadata             = Metadata.FormVersion,
      languageOpt          = None,
      select               = Local,
      matchTypeAndValueOpt = (MatchType.Exact, FormDefinitionVersion.Latest).some,
      orderDirectionOpt    = None
    )

  def modifiedSinceQuery(modifiedSince: Instant): Query[Instant] =
    Query(
      metadata             = Metadata.LastModified,
      languageOpt          = None,
      select               = Local,
      matchTypeAndValueOpt = (MatchType.GreaterThan, modifiedSince).some,
      orderDirectionOpt    = None
    )

  val defaultSortQuery: Query[Instant] =
    Query[Instant](
      metadata             = Metadata.LastModified,
      languageOpt          = None,
      select               = Max, // Most recent between local and remote forms
      matchTypeAndValueOpt = None,
      orderDirectionOpt    = OrderDirection.Descending.some
    )
}
