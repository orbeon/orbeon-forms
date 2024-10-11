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
import org.orbeon.oxf.fr.persistence.relational.form.adt.LocalRemoteOrCombinator.{Combinator, Remote}


trait FilterOrSortQuery[T] {
  def metadata               : Metadata[T]
  def languageOpt            : Option[String]
  def localRemoteOrCombinator: LocalRemoteOrCombinator

  def effectiveLanguageOpt(formRequest: FormRequest): Option[String] =
    languageOpt orElse formRequest.languageOpt

  def elem(name: String, value: String, atts: (String, String)*): xml.Elem = {
    val metadataOpt    = ("metadata" -> metadata.string).some
    val languageAttOpt = languageOpt.map("xml:lang" -> _)
    val urlOpt         = localRemoteOrCombinator.some.collect { case Remote(url)   => "url"        -> url }
    val combinatorOpt  = localRemoteOrCombinator.some.collect { case c: Combinator => "combinator" -> c.string }

    Form.elem(name, value, (Seq(metadataOpt, languageAttOpt, urlOpt, combinatorOpt).flatten ++ atts) *)
  }
}
