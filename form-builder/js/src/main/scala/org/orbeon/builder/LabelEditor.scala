/**
 * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.builder

import org.orbeon.builder.BlockCache.Block
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.xforms.$
import org.orbeon.xforms._
import org.orbeon.xforms.facade.AjaxServer

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.jquery.{JQuery, JQueryEventObject}

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel, ScalaJSDefined}

@JSExportTopLevel("ORBEON.builder.LabelEditor")
@JSExportAll
object LabelEditor {

  val SectionTitleSelector = ".fr-section-title:first"
  val SectionLabelSelector = ".fr-section-label:first a, .fr-section-label:first .xforms-output-output"

  var labelInput: js.UndefOr[JQuery] = js.undefined

  // On click on a trigger inside .fb-section-editor, send section id as a property along with the event
  AjaxServer.beforeSendingEvent.add((
    event         : js.Dynamic,
    addProperties : js.Function1[js.Dictionary[String], Unit]
  ) ⇒ {

    val eventTargetId    = event.targetId.asInstanceOf[String]
    val eventName        = event.eventName.asInstanceOf[String]
    val targetEl         = $(dom.document.getElementById(eventTargetId))
    val inSectionEditor  = targetEl.closest(".fb-section-editor").is("*")

    if (eventName == "DOMActivate" && inSectionEditor)
      addProperties(js.Dictionary(
        "section-id" → SideEditor.currentSectionOpt.get.attr("id").get
      ))
  })

}
