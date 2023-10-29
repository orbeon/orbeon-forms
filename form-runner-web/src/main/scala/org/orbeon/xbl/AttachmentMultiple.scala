/**
  * Copyright (C) 2020 Orbeon, Inc.
  *
  * This program is free software you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.log4s.Logger
import org.orbeon.facades.Bowser
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.web.DomEventNames
import org.orbeon.xforms._
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.concurrent.duration._
import scala.scalajs.js


object AttachmentMultiple {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.AttachmentMultiple")

  XBL.declareCompanion("fr|attachment",          js.constructorOf[AttachmentCompanion])
  XBL.declareCompanion("fr|attachment-multiple", js.constructorOf[AttachmentCompanion])

  private class AttachmentCompanion(containerElem: html.Element) extends XBLCompanion {

    companion =>

    import Private._

    override def init(): Unit = {

      logger.debug("init")

      val isStaticReadonly = uploadInputOpt.isEmpty

      if (! isStaticReadonly && ! companion.isMarkedReadonly && browserSupportsFileDrop) {
        registerAllListeners()
      } else if (! browserSupportsFileDrop) {
        logger.debug("disabling drag and drop of files for unsupported browser")
        dropElem.classList.add("xforms-hidden")
      }
    }

    override def destroy(): Unit = {
      logger.debug("destroy")
      EventSupport.clearAllListeners()
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit =
      if (readonly)
        EventSupport.clearAllListeners()
      else
        registerAllListeners()

    private object Private {

      def dropElem          = containerElem.querySelector(".fr-attachment-drop")
      def selectAnchor      = containerElem.querySelector(".fr-attachment-select").asInstanceOf[html.Anchor]
      def uploadControlElem = containerElem.querySelector(".xforms-upload").asInstanceOf[html.Element]
      def uploadInputOpt    = Option(containerElem.querySelector(".xforms-upload-select").asInstanceOf[html.Input])

      object EventSupport extends EventListenerSupport

      def browserSupportsFileDrop: Boolean =
        ! Bowser.msie.contains(true)

      def registerAllListeners(): Unit = {
        // "A listener for the dragenter and dragover events are used to indicate valid drop targets,
        // that is, places where dragged items may be dropped. Most areas of a web page or application
        // are not valid places to drop data. Thus, the default handling of these events is not to allow
        // a drop."
        // https://developer.mozilla.org/en-US/docs/Web/API/HTML_Drag_and_Drop_API/Drag_operations#droptargets

        // "If you want to allow a drop, you must prevent the default handling by cancelling both the dragenter
        // and dragover events."

        def addClass() =
          dropElem.classList.add("fr-attachment-dragover")

        def removeClass() =
          dropElem.classList.remove("fr-attachment-dragover")

        def addListenerOnDropElem(name: String, fn: dom.raw.DragEvent => Unit): Unit =
          EventSupport.addListener(dropElem, name, fn)

        addListenerOnDropElem(
          DomEventNames.Drop,
          ev => {
            removeClass()
            if (ev.dataTransfer.types contains "Files") {
              logger.debug(s"${ev.`type`} with files")
              ev.preventDefault()

              val files = ev.dataTransfer.files
              for (i <- 0 until files.length) {
                UploaderClient.uploadEventQueue.add(
                  event    = UploadEvent(Page.getUploadControl(uploadControlElem), files(i)),
                  wait     = Page.getXFormsFormFromHtmlElemOrThrow(companion.containerElem).configuration.delayBeforeIncrementalRequest.millis,
                  waitType = ExecutionWait.MinWait
                )
              }
            }
          }
        )

        addListenerOnDropElem(
          DomEventNames.DragOver,
          ev => {
            ev.preventDefault() // Necessary to indicate the drop target
            // "add an entry to L consisting of the string "Files""
            if (ev.dataTransfer.types contains "Files") {
              logger.debug(s"${ev.`type`} with files")
              addClass()
            }
          }
        )

        addListenerOnDropElem(
          DomEventNames.DragLeave, // doesn't seem like `dragexit` is a thing anymore
          ev => {
            logger.debug(ev.`type`)
            if (ev.target eq dropElem) {
              ev.preventDefault()
              removeClass()
            }
          }
        )

        EventSupport.addListener(
          dropElem,
          DomEventNames.KeyDown,
          (ev: dom.raw.KeyboardEvent) => {
            if (ev.key == "Enter" || ev.key == " ") {
              ev.preventDefault() // so that the page doesn't scroll
              uploadInputOpt foreach (_.click())
            }
          }
        )

        EventSupport.addListener(
          selectAnchor,
          DomEventNames.Click,
          (_: dom.raw.EventTarget) => uploadInputOpt foreach (_.click())
        )
      }
    }
  }
}