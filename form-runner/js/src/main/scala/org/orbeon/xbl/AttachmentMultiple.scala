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

import org.orbeon.facades.Bowser
import org.orbeon.xforms.facade.{Properties, XBL, XBLCompanion}
import org.orbeon.xforms._
import org.scalajs.dom
import org.scalajs.dom.html

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

// Companion for `fr:dnd-repeat`
object AttachmentMultiple {

  XBL.declareCompanion(
    "fr|attachment",
    newXBLCompanion
  )

  XBL.declareCompanion(
    "fr|attachment-multiple",
    newXBLCompanion
  )

  private def newXBLCompanion: XBLCompanion =
    new XBLCompanion {

      companion =>

      import Private._

      override def init(): Unit = {

        scribe.debug("init")

        val isStaticReadonly = uploadInputOpt.isEmpty

        // https://github.com/orbeon/orbeon-forms/issues/4562
        uploadInputOpt foreach { uploadInput =>
          val label = selectLabel
          label.htmlFor = Page.namespaceIdIfNeeded(uploadInput.form.id, label.htmlFor)
        }

        if (! isStaticReadonly && ! companion.isMarkedReadonly && browserSupportsFileDrop) {
          registerAllListeners()
        } else if (! browserSupportsFileDrop) {
          scribe.debug("disabling drag and drop of files for unsupported browser")
          dropElem.classList.add("xforms-hidden")
        }
      }

      override def destroy(): Unit = {
        scribe.debug("destroy")
        EventSupport.clearAllListeners()
      }

      override def xformsUpdateReadonly(readonly: Boolean): Unit =
        if (readonly)
          EventSupport.clearAllListeners()
        else
          registerAllListeners()

      private object Private {

        object EventSupport extends EventListenerSupport

        def dropElem          = containerElem.querySelector(".fr-attachment-drop")
        def selectLabel       = containerElem.querySelector(".fr-attachment-select").asInstanceOf[html.Label]

        def uploadControlElem = containerElem.querySelector(".xforms-upload").asInstanceOf[html.Element]
        def uploadInputOpt    = Option(containerElem.querySelector(".xforms-upload-select").asInstanceOf[html.Input])

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
            "drop",
            ev => {
              removeClass()
              if (ev.dataTransfer.types contains "Files") {
                scribe.debug(s"${ev.`type`} with files")
                ev.preventDefault()

                val files = ev.dataTransfer.files
                for (i <- 0 until files.length) {
                  UploaderClient.uploadEventQueue.add(
                    event    = UploadEvent(Page.getUploadControl(uploadControlElem), files(i)),
                    wait     = Properties.delayBeforeIncrementalRequest.get().millis,
                    waitType = ExecutionWait.MinWait
                  )
                }
              }
            }
          )

          addListenerOnDropElem(
            "dragover",
            ev => {
              ev.preventDefault() // Necessary to indicate the drop target
              // "add an entry to L consisting of the string "Files""
              if (ev.dataTransfer.types contains "Files") {
                scribe.debug(s"${ev.`type`} with files")
                addClass()
              }
            }
          )

          addListenerOnDropElem(
            "dragleave", // doesn't seem like `dragexit` is a thing anymore
            ev => {
              scribe.debug(ev.`type`)
              if (ev.target eq dropElem) {
                ev.preventDefault()
                removeClass()
              }
            }
          )

          EventSupport.addListener(
            selectLabel,
            "keydown",
            (ev: dom.raw.KeyboardEvent) => {
              if (ev.key == "Enter" || ev.key == " ") {
                ev.preventDefault() // so that the page doesn't scroll
                uploadInputOpt foreach (_.click())
              }
            }
          )
        }
      }
    }
}