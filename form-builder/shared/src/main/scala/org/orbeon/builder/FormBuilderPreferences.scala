/**
 * Copyright (C) 2026 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.builder

import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import org.orbeon.oxf.util.PathUtils.UrlEncoderDecoder
import org.orbeon.oxf.util.StringUtils.*

import scala.util.Try


case class FormBuilderPreferences(
  controlSettingsToolbarVisible: Boolean = true,
  showCellBadges               : Boolean = true,
  outlineVisible               : Boolean = true
)

object FormBuilderPreferences {

  val PreferencesCookieName            = "orbeon-fb-preferences"
  val ControlSettingsToolbarVisibleKey = "control-settings-toolbar-visible"
  val ShowCellBadgesKey                = "show-cell-badges"
  val OutlineVisibleKey                = "outline-visible"

  val Default: FormBuilderPreferences = FormBuilderPreferences()

  implicit val encoder: Encoder[FormBuilderPreferences] = Encoder.instance { prefs =>
    Json.obj(
      ControlSettingsToolbarVisibleKey -> Json.fromBoolean(prefs.controlSettingsToolbarVisible),
      ShowCellBadgesKey                -> Json.fromBoolean(prefs.showCellBadges),
      OutlineVisibleKey                -> Json.fromBoolean(prefs.outlineVisible)
    )
  }

  implicit val decoder: Decoder[FormBuilderPreferences] = Decoder.instance { c =>
    for {
      controlSettingsToolbarVisible <- c.getOrElse[Boolean](ControlSettingsToolbarVisibleKey)(Default.controlSettingsToolbarVisible)
      showCellBadges                <- c.getOrElse[Boolean](ShowCellBadgesKey)(Default.showCellBadges)
      outlineVisible                <- c.getOrElse[Boolean](OutlineVisibleKey)(Default.outlineVisible)
    } yield FormBuilderPreferences(
      controlSettingsToolbarVisible = controlSettingsToolbarVisible,
      showCellBadges                = showCellBadges,
      outlineVisible                = outlineVisible
    )
  }

  def toCookieValue(prefs: FormBuilderPreferences)(implicit ed: UrlEncoderDecoder): String =
    ed.encode(prefs.asJson.noSpaces).replace("+", "%20")

  def fromCookieValue(cookieValue: String)(implicit ed: UrlEncoderDecoder): Option[FormBuilderPreferences] =
    for {
      trimmed <- cookieValue.trimAllToOpt
      decoded <- Try(ed.decode(trimmed)).toOption
      prefs   <- decode[FormBuilderPreferences](decoded).toOption
    } yield prefs

  def fromCookieHeader(cookieHeader: String)(implicit ed: UrlEncoderDecoder): Option[FormBuilderPreferences] =
    cookieHeader
      .splitTo[List](";")
      .flatMap(_.trimAllToOpt)
      .find(_.startsWith(s"$PreferencesCookieName="))
      .map(_.substring(PreferencesCookieName.length + 1).trimAllToEmpty)
      .flatMap(fromCookieValue)
}
