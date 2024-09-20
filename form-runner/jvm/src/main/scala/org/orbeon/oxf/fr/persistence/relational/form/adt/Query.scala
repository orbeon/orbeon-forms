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
import org.orbeon.oxf.fr.FormRunner.getDefaultLang
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.NodeInfoOps


case class Query[T](
  metadata            : Metadata[T],
  languageOpt         : Option[String],
  urlOpt              : Option[String], // Local form metadata if None, remote form metadata if Some(url)
  matchTypeAndValueOpt: Option[(MatchType, T)],
  orderDirectionOpt   : Option[OrderDirection]
) {
  def toXML: NodeInfo = {

    val nodeValueOpt   = matchTypeAndValueOpt.map(_._2)       .map(metadata.valueAsString)
    val languageAttOpt = languageOpt                          .map(xml.Attribute(None.orNull, "xml:lang", _, xml.Null))
    val urlAttOpt      = urlOpt                               .map(xml.Attribute(None.orNull, "url"     , _, xml.Null))
    val matchAttOpt    = matchTypeAndValueOpt.map(_._1.string).map(xml.Attribute(None.orNull, "match"   , _, xml.Null))
    val sortAttOpt     = orderDirectionOpt   .map(_.sql)      .map(xml.Attribute(None.orNull, "sort"    , _, xml.Null))

    val baseElem = <query metadata={metadata.string}>{nodeValueOpt.getOrElse("")}</query>

    Seq(languageAttOpt, urlAttOpt, matchAttOpt, sortAttOpt).flatten.foldLeft(baseElem)((elem, attribute) => elem % attribute)
  }

  def satisfies(form: Form, formRequestLanguageOpt: Option[String]): Boolean =
    matchTypeAndValueOpt match {
      case None                     => true // No match type and value means this is a sort-only query
      case Some((matchType, queryValue)) =>

        // Select either local or remote metadata based on the URL
        val formMetadataOpt = urlOpt match {
          case Some(url) => form.remoteMetadata.get(url)
          case None      => form.localMetadataOpt
        }

        // Should we cache the language at least for the duration of the whole API request? Make it lazy for the time being.
        lazy val language = languageOpt orElse formRequestLanguageOpt getOrElse getDefaultLang(form.appForm.some)

        val formValueOpt = metadata.valueOpt(form, formMetadataOpt, language)

        formValueOpt match {
          case None            => false // No form value was found (e.g. query on local metadata, but only remote metadata is available)
          case Some(formValue) => matchType.satisfies(formValue, queryValue, metadata)
        }
    }
}

object Query {
  def apply[T](xml: NodeInfo): Query[T] = {

    // TODO: fix type design/inference
    val metadata: Metadata[T] = Metadata(xml.attValue("metadata")).asInstanceOf[Metadata[T]]
    val urlOpt                = xml.attValueOpt("url")

    if (urlOpt.isDefined && ! metadata.supportsUrl) {
      throw new IllegalArgumentException(s"Query with metadata `${metadata.string}` cannot be applied to a remote server")
    }

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
      urlOpt               = urlOpt,
      matchTypeAndValueOpt = matchTypeAndValueOpt,
      orderDirectionOpt    = orderDirectionOpt
    )
  }
}
