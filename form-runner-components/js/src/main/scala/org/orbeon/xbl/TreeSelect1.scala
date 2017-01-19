/**
  * Copyright (C) 2017 Orbeon, Inc.
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

import org.orbeon.fr._
import org.orbeon.xforms
import org.orbeon.xforms._
import org.scalajs.jquery.JQueryEventObject

import scala.scalajs.js

object TreeSelect1 {

  case class TreeConfiguration(tree: Fancytree, handlers: List[js.Function])

  xforms.XBL.declareCompanion(
    "fr|tree-select1",
    new XBLCompanion {

      def findTreeContainer         = $(containerElem).find(".xbl-fr-tree-select1-container")
      def removeListeners()         = findTreeContainer.off()
      def logDebug(message: String) = () // println(s"TreeSelect: $message")

      var isReadonly        : Boolean                   = false
      var hasJavaScriptTree : Boolean                   = true
      var currentValue      : String                    = ""
      var treeConfiguration : Option[TreeConfiguration] = None

      override def init(): Unit = {
        logDebug("init")
        this.isReadonly        = $(containerElem).is(".xforms-readonly")
        this.hasJavaScriptTree = ! $(containerElem).find(".xbl-fr-tree-select1-container-static-readonly").is(":not('.xforms-disabled')")
      }

      override def destroy(): Unit =
        if (this.hasJavaScriptTree) {
          removeListeners()
          this.currentValue      = ""
          this.treeConfiguration = None
        }

      override def xformsUpdateReadonly(readonly: Boolean): Unit =
        if (this.hasJavaScriptTree) {
          this.isReadonly = readonly
        }

      override def xformsGetValue(): String = this.currentValue

      override def xformsUpdateValue(newValue: String): Unit =
        if (this.hasJavaScriptTree) {
          logDebug(s"xformsUpdateValue = `$newValue`")

          this.currentValue = newValue ensuring (_ ne null)

          val activatedNodeOpt = this.treeConfiguration flatMap (c ⇒ Option(c.tree.activateKey(newValue)))

          if (activatedNodeOpt.isEmpty)
            logDebug(s"no matching node for `$newValue`")
        }

      override def xformsFocus(): Unit =
        if (this.hasJavaScriptTree) {
          logDebug("xformsFocus")
        }

      // This is called explicitly from tree-select1.xbl upon `xforms-enabled` and itemset change
      def updateItemset(itemset: String): Unit =
        if (this.hasJavaScriptTree) {

          logDebug(s"itemset = `$itemset`")

          def convertItems(items: js.Array[Item]): js.Array[FancytreeJsonNode] =
            for (item ← items)
              yield FancytreeJsonNode(
                item.label,
                item.value,
                item.children map convertItems,
                item.attributes flatMap (_.`xxforms-open`) map (_ == "true"),
                item.attributes flatMap (_.`class`)
              )

          val jTreeContainer        = findTreeContainer
          val jTreeContainerDynamic = jTreeContainer.asInstanceOf[js.Dynamic]

          val items = convertItems(js.JSON.parse(itemset).asInstanceOf[js.Array[Item]])

          this.treeConfiguration match {
            case Some(TreeConfiguration(tree, _)) ⇒
              tree.reload(items)
            case None ⇒
              jTreeContainerDynamic.fancytree(new js.Object { val source = items })
              jTreeContainerDynamic.fancytree("option", "toggleEffect", false)

              val onClick: js.Function = (event: JQueryEventObject, data: js.Dynamic) ⇒ {
                // Always allow click on expanders but don't allow selection when readonly
                data.targetType.asInstanceOf[String] == "expander" || ! this.isReadonly
              }

              val onActivate: js.Function = (event: JQueryEventObject, data: js.Dynamic) ⇒
                if (! this.isReadonly) {
                  val newValue = data.node.key.asInstanceOf[String] ensuring (_ ne null)

                  if (this.currentValue != newValue) {
                    this.currentValue = newValue

                    xforms.Document.setValue(
                        containerElem.id,
                        newValue
                    )
                  }
                }

              val onDeactivate: js.Function = (event: JQueryEventObject, data: js.Dynamic) ⇒ ()

              jTreeContainer.on("fancytreeclick",      onClick)
              jTreeContainer.on("fancytreeactivate",   onActivate)
              jTreeContainer.on("fancytreedeactivate", onDeactivate)

              this.treeConfiguration = Some(
                TreeConfiguration(
                  jTreeContainerDynamic.fancytree("getTree").asInstanceOf[Fancytree],
                  List(onActivate, onDeactivate)
                )
              )
          }

          // Make sure to update the value into the tree
          xformsUpdateValue(this.currentValue)
        }
    }
  )
}