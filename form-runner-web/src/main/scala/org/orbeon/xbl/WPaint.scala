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

import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.web.DomEventNames
import org.orbeon.xforms.Constants.DUMMY_IMAGE_URI
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{$, AjaxClient, AjaxEvent}
import org.scalajs.dom.{document, html}
import org.scalajs.jquery.JQuery

import scala.scalajs.js
import scala.scalajs.js.Dynamic
import scala.scalajs.js.JSConverters.JSRichOption


object WPaint {

  XBL.declareCompanion("fr|wpaint", js.constructorOf[WPaintCompanion])

  private class WPaintCompanion(containerElem: html.Element) extends XBLCompanion {

    def annotationEl : JQuery = $(containerElem).find(".fr-wpaint-annotation img")
    def imageEl      : JQuery = $(containerElem).find(".fr-wpaint-image img")
    def noCanvasEl   : JQuery = $(containerElem).find(".fr-wpaint-no-canvas")
    def wpaintElA    : JQuery = $(containerElem).find(".fr-wpaint-container-a")
    def wpaintElB    : JQuery = $(containerElem).find(".fr-wpaint-container-b")
    def uploadEl     : JQuery = $(containerElem).find(".xforms-upload")
    var wpaintElC    : JQuery = null

    override def init(): Unit = {
      if (canvasSupported) {

        // Hide the canvas element used just to show the browser doesn't support the canvas feature, and show the image selector
        // We don't remove it as it contains an `xf:output` which needs to be there in case the FR language changes.
        noCanvasEl.addClass("xforms-hidden")
        uploadEl.removeClass("xforms-hidden")

        // Register events
        imageEl.asInstanceOf[Dynamic].imagesLoaded(backgroundImageChanged _)
      }
    }

    def enabled  () = ()
    def readonly () = ()
    def readwrite() = ()

    private def backgroundImageChanged(): Unit = {
      if (imageEl.attr("src") contains DUMMY_IMAGE_URI) {
        wpaintElA.addClass("xforms-hidden")
        if (wpaintElC != null) {
            wpaintElC.detach()
            wpaintElC = null
        }
      } else {
        wpaintElA.removeClass("xforms-hidden")
        wpaintElA.css("width" , imageEl.width().toString + "px")
        wpaintElB.css("padding-top", (imageEl.height() / imageEl.width() * 100).toString + "%")
        // tabindex="-1" allows div to have the focus, used to send change on blur
        wpaintElC = $("""<div class="fr-wpaint-container-c" tabindex="-1"/>""")
        wpaintElB.append(wpaintElC)
        // When looses focus, send drawing to the server right away (incremental)
        wpaintElC.on(DomEventNames.FocusOut, sendAnnotationToServer _)
        wpaintElC.css("width",  imageEl.width() )
        wpaintElC.css("height", imageEl.height())
        val annotation = annotationEl.attr("src").get
        val startStrokeColor = {
          val classes = wpaintElA.get(0).classList.asInstanceOf[js.Array[String]]
          val ClassPrefix = "fr-wpaint-start-stroke-color-"
          val colorOpt = classes.toList.find(_.startsWith(ClassPrefix)).map(_.substringAfter(ClassPrefix))
          colorOpt.map("#" + _).orUndefined
        }
        wpaintElC.asInstanceOf[Dynamic].wPaint(new js.Object {
          val drawDown: js.Function = () => wpaintElC.focus()
          val imageBg               = imageEl.attr("src")
          val image                 = if (annotation == "") null else annotation
          val strokeStyle           = startStrokeColor
        })
      }

      // Re-register listener, as imagesLoaded() calls listener only once
      imageEl.one("load", backgroundImageChanged _)
    }

    // Test canvas support, see http://stackoverflow.com/a/2746983/5295
    private def canvasSupported: Boolean = {
      val testCanvas = document.createElement("canvas").asInstanceOf[Dynamic]
      (! js.isUndefined(testCanvas.getContext)) &&
        (! js.isUndefined(testCanvas.getContext("2d")))
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
