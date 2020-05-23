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

import org.orbeon.facades.{Fancytree, FancytreeEventData, FancytreeJsonNode}
import org.orbeon.xforms
import org.orbeon.xforms.$
import org.orbeon.xforms.facade.{Item, XBL, XBLCompanion}
import org.scalajs.jquery.JQueryEventObject

import scala.scalajs.js

object TreeSelect1 {

  XBL.declareCompanion(
    "fr|tree-select1",
    new XBLCompanion {

      def findTreeContainer         = $(containerElem).find(".xbl-fr-tree-select1-container")
      def removeListeners()         = findTreeContainer.off()
      def logDebug(message: String) = () // println(s"TreeSelect: $message")

      var isReadonly        : Boolean           = false
      var hasJavaScriptTree : Boolean           = true
      var currentValue      : String            = ""
      var treeConfiguration : Option[Fancytree] = None

      override def init(): Unit = {
        logDebug("init")
        this.isReadonly        = containerElem.classList.contains("xforms-readonly")
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

      override def xformsUpdateValue(newValue: String): js.UndefOr[Nothing] = {
        if (this.hasJavaScriptTree) {
          logDebug(s"xformsUpdateValue = `$newValue`")

          this.currentValue = newValue ensuring (_ ne null)

          val activatedNodeOpt = this.treeConfiguration flatMap (tree => Option(tree.activateKey(newValue)))

          if (activatedNodeOpt.isEmpty)
            logDebug(s"no matching node for `$newValue`")
        }
        js.undefined
      }

      override def xformsFocus(): Unit =
        if (this.hasJavaScriptTree) {
          logDebug("xformsFocus")
        }

      // This is called explicitly from tree-select1.xbl upon `xforms-enabled` and itemset change
      def updateItemset(itemset: String): Unit =
        if (this.hasJavaScriptTree) {

          logDebug(s"itemset = `$itemset`")

          def itemOpen(item: Item) =
            item.attributes flatMap (_.`xxforms-open`) exists (_ == "true")

          def itemChildrenOpen(nodesOrUndef: js.UndefOr[js.Array[FancytreeJsonNode]]) =
            nodesOrUndef exists (_ exists (_.expanded exists identity))

          def convertItems(items: js.Array[Item]): js.Array[FancytreeJsonNode] =
            for {
              item            <- items
              childrenOrUndef = item.children map convertItems
            } yield
              FancytreeJsonNode(
                label           = item.label,
                value           = item.value,
                open            = itemOpen(item) || itemChildrenOpen(childrenOrUndef),
                classesOrUndef  = item.attributes flatMap (_.`class`),
                childrenOrUndef = childrenOrUndef
              )

          val jTreeContainer        = findTreeContainer
          val jTreeContainerDynamic = jTreeContainer.asInstanceOf[js.Dynamic]

          val items = convertItems(js.JSON.parse(itemset).asInstanceOf[js.Array[Item]])

          this.treeConfiguration match {
            case Some(tree) =>
              tree.reload(items)
            case None =>
              jTreeContainerDynamic.fancytree(new js.Object { val source = items })
              jTreeContainerDynamic.fancytree("option", "toggleEffect", false)

              // Always allow click on expanders but don't allow selection when readonly
              val onClick: js.Function = (event: JQueryEventObject, data: FancytreeEventData) =>
                (data.targetType exists (_ == "expander")) || ! this.isReadonly

              val onActivate: js.Function = (event: JQueryEventObject, data: FancytreeEventData) =>
                if (! this.isReadonly) {
                  val newValue = data.node.key ensuring (_ ne null)

                  if (this.currentValue != newValue) {
                    this.currentValue = newValue

                    xforms.DocumentAPI.setValue(
                        containerElem.id,
                        newValue
                    )
                  }
                }

              jTreeContainer.on("fancytreeclick",    onClick)
              jTreeContainer.on("fancytreeactivate", onActivate)

              this.treeConfiguration = Some(jTreeContainerDynamic.fancytree("getTree").asInstanceOf[Fancytree])
          }

          // Make sure to update the value into the tree
          xformsUpdateValue(this.currentValue)
        }
    }
  )
}