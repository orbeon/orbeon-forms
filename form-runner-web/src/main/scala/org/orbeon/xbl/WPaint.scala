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
package org.orbeon.xbl

import io.udash.wrappers.jquery.JQuery
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.Constants.DummyImageUri
import org.orbeon.xforms.XFormsXbl
import org.orbeon.xforms.facade.XBLCompanion
import org.orbeon.xforms.{$, AjaxClient, AjaxEvent}
import org.scalajs.dom
import org.scalajs.dom.{EventListenerOptions, html}

import scala.scalajs.js
import scala.scalajs.js.Dynamic
import scala.scalajs.js.JSConverters.JSRichOption


object WPaint {

  XFormsXbl.declareCompanion("fr|wpaint", js.constructorOf[WPaintCompanion])

  private class WPaintCompanion(containerElem: html.Element) extends XBLCompanion {

    def annotationEl : html.Image = containerElem.queryNestedElems[html.Image](".fr-wpaint-annotation img").head
    def imageEl      : html.Image = containerElem.queryNestedElems[html.Image](".fr-wpaint-image img").head
    def wpaintElA    : html.Div   = containerElem.queryNestedElems[html.Div](".fr-wpaint-container-a").head
    def wpaintElB    : html.Div   = containerElem.queryNestedElems[html.Div](".fr-wpaint-container-b").head
    var wpaintElC    : JQuery     = null

    override def init(): Unit =
      $(imageEl).asInstanceOf[Dynamic].imagesLoaded(backgroundImageChanged _)

    def enabled  () = ()
    def readonly () = ()
    def readwrite() = ()

    private def backgroundImageChanged(): Unit = {
      val imageElJQuery = $(imageEl)
      if (imageEl.getAttribute("src") contains DummyImageUri) {
        wpaintElA.classList.add("xforms-hidden")
        if (wpaintElC != null) {
            wpaintElC.detach()
            wpaintElC = null
        }
      } else {
        wpaintElA.classList.remove("xforms-hidden")
        wpaintElA.style.width = imageElJQuery.width().toString + "px"
        wpaintElB.style.paddingTop = (imageElJQuery.height().toDouble / imageElJQuery.width() * 100).toString + "%"
        // tabindex="-1" allows div to have the focus, used to send change on blur
        wpaintElC = $("""<div class="fr-wpaint-container-c" tabindex="-1"/>""")
        $(wpaintElB).append(wpaintElC)
        // When looses focus, send drawing to the server right away (incremental)
        wpaintElC.get().foreach(_.addEventListener(DomEventNames.FocusOut, (_: dom.Event) => sendAnnotationToServer()))
        wpaintElC.css("width",  imageElJQuery.width() )
        wpaintElC.css("height", imageElJQuery.height())
        val annotationSrcOpt = annotationEl.src.trimAllToOpt
        val startStrokeColor = {
          val classes = wpaintElA.classList
          val ClassPrefix = "fr-wpaint-start-stroke-color-"
          val colorOpt = classes.find(_.startsWith(ClassPrefix)).map(_.substringAfter(ClassPrefix))
          colorOpt.map("#" + _).orUndefined
        }
        wpaintElC.asInstanceOf[Dynamic].wPaint(new js.Object {
          val drawDown: js.Function = () => wpaintElC.trigger("focus")
          val imageBg               = imageEl.src
          val image                 = annotationSrcOpt.orNull
          val strokeStyle           = startStrokeColor
        })
      }

      // Re-register listener, as imagesLoaded() calls listener only once
      imageEl.addEventListener(
        `type`   = "load",
        listener = (_: dom.Event) => backgroundImageChanged(),
        options  = new EventListenerOptions { once = true })
    }

    // Send the image data from wPaint to the server, which will put it in <annotation>
    private def sendAnnotationToServer(): Unit = {
      val annotationImgData = wpaintElC.asInstanceOf[Dynamic].wPaint("image")
      AjaxClient.fireEvent(
        AjaxEvent(
          eventName  = "fr-update-annotation",
          targetId   = containerElem.id,
          properties = Map("value" -> annotationImgData.toString)
        )
      )
    }
  }
}
