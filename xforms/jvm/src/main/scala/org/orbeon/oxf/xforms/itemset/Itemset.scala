/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.itemset

import org.orbeon.dom.{Namespace, QName}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xforms.itemset.ItemsetSupport.isSelected
import org.orbeon.oxf.xforms.{XFormsConstants, XFormsUtils}
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.oxf.xml.{TransformerUtils, XMLReceiver}
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.saxon.tinytree.TinyBuilder
import org.orbeon.saxon.{Configuration, om}
import org.xml.sax.SAXException


trait ItemsetListener {
  def startLevel(itemNode: ItemNode)
  def endLevel()
  def startItem(itemNode: ItemNode, first: Boolean)
  def endItem(itemNode: ItemNode)
}

class Itemset(val multiple: Boolean, val hasCopy: Boolean) extends ItemContainer {

  import Itemset._

  def iterateSelectedItems(
    dataValue                  : Item.Value[om.NodeInfo],
    compareAtt                 : om.NodeInfo => Boolean,
    excludeWhitespaceTextNodes : Boolean
  ): Iterator[Item.ValueNode] =
    allItemsWithValueIterator(reverse = false) collect {
      case (item, itemValue) if isSelected(multiple, dataValue, itemValue, compareAtt, excludeWhitespaceTextNodes) => item
    }

  // Return the list of items as a JSON tree with hierarchical information
  def asJSON(
    controlValue               : Option[(Item.Value[om.NodeInfo], om.NodeInfo => Boolean)],
    encode                     : Boolean,
    excludeWhitespaceTextNodes : Boolean,
    locationData               : LocationData
  ): String = {

    val sb = new StringBuilder
    // Array of top-level items
    sb.append("[")
    try {
      visit(new ItemsetListener {

        def startItem(itemNode: ItemNode, first: Boolean): Unit = {
          if (! first)
            sb.append(',')

          // Start object
          sb.append("{")

          // Item LHH and value
          sb.append(""""label":"""")
          sb.append(itemNode.javaScriptLabel(locationData))
          sb.append('"')

          itemNode match {
            case item: Item.ValueNode =>
              item.javaScriptHelp(locationData) foreach { h =>
                sb.append(""","help":"""")
                sb.append(h)
                sb.append('"')
              }
              item.javaScriptHint(locationData) foreach { h =>
                sb.append(""","hint":"""")
                sb.append(h)
                sb.append('"')
              }
              sb.append(""","value":"""")
              sb.append(item.javaScriptValue(encode))
              sb.append('"')
            case _ =>
          }

          // Item attributes if any
          val attributes = itemNode.attributes
          if (attributes.nonEmpty) {
            sb.append(""","attributes":{""")

            val nameValues =
              for {
                (name, value) <- attributes
                escapedName   = XFormsUtils.escapeJavaScript(getAttributeName(name))
                escapedValue  = XFormsUtils.escapeJavaScript(value)
              } yield
                s""""$escapedName":"$escapedValue""""

            sb.append(nameValues mkString ",")

            sb.append('}')
          }

          // Handle selection
          itemNode match {
            case item: Item.ValueNode if controlValue exists { case (dataValue, compareAtt) =>
              isSelected(multiple, dataValue, item.value, compareAtt, excludeWhitespaceTextNodes)
            } => sb.append(""","selected":true""")
            case _ =>
          }

          // Start array of children items
          if (itemNode.hasChildren)
            sb.append(""","children":[""")
        }

        def endItem(itemNode: ItemNode): Unit = {
          // End array of children items
          if (itemNode.hasChildren)
            sb.append(']')

          // End object
          sb.append("}")
        }

        def startLevel(itemNode: ItemNode): Unit = ()
        def endLevel(): Unit = ()
      })
    } catch {
      case e: SAXException =>
        throw new ValidationException("Error while creating itemset tree", e, locationData)
    }
    sb.append("]")

    sb.toString
  }

  // Return the list of items as an XML tree
  def asXML(
    configuration              : Configuration,
    controlValue               : Option[(Item.Value[om.NodeInfo], om.NodeInfo => Boolean)],
    excludeWhitespaceTextNodes : Boolean,
    locationData               : LocationData
  ): DocumentInfo = {

    val treeBuilder = new TinyBuilder
    val identity = TransformerUtils.getIdentityTransformerHandler(configuration)
    identity.setResult(treeBuilder)

    implicit val xmlReceiver: XMLReceiver = identity

    withDocument {
      withElement("itemset") {
        if (hasChildren) {
          visit(new ItemsetListener {

            def startLevel(itemNode: ItemNode): Unit =
              openElement("choices")

            def endLevel(): Unit =
              closeElement("choices")

            def startItem(itemNode: ItemNode, first: Boolean): Unit = {

              val itemAttributes =
                itemNode match {
                  case item: Item.ValueNode if controlValue exists { case (dataValue, compareAtt) =>
                    isSelected(multiple, dataValue, item.value, compareAtt, excludeWhitespaceTextNodes)
                  } =>
                    List("selected" -> "true")
                  case _ =>
                    Nil
                }

              // TODO: Item attributes if any

              openElement("item", atts = itemAttributes)

              withElement("label") {
                itemNode.label.streamAsHTML(locationData)
              }

              itemNode match {
                case item: Item.ValueNode =>
                  item.help foreach { h =>
                    withElement("help") {
                      h.streamAsHTML(locationData)
                    }
                  }

                  item.hint foreach { h =>
                    withElement("hint") {
                      h.streamAsHTML(locationData)
                    }
                  }

                  val itemExternalValue = item.externalValue(encode = false)
                  withElement("value") {
                    if (itemExternalValue.nonEmpty)
                      text(itemExternalValue)
                  }
                case _ =>
              }
            }

            def endItem(itemNode: ItemNode): Unit =
              closeElement("item")
          })
        }
      }
    }

    treeBuilder.getCurrentRoot.asInstanceOf[DocumentInfo]
  }
}

object Itemset {
  def getAttributeName(name: QName): String =
    if (name.namespace == Namespace.EmptyNamespace)
      name.localName
    else if (name.namespace == XFormsConstants.XXFORMS_NAMESPACE)
      "xxforms-" + name.localName
    else
      throw new IllegalStateException("Invalid attribute on item: " + name.localName)
}