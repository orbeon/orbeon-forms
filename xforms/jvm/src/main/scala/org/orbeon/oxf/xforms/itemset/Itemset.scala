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
import org.orbeon.oxf.xforms.itemset.XFormsItemUtils.isSelected
import org.orbeon.oxf.xforms.{XFormsConstants, XFormsUtils}
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.oxf.xml.{TransformerUtils, XMLReceiverHelper}
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.saxon.tinytree.TinyBuilder
import org.orbeon.saxon.{Configuration, om}
import org.xml.sax.SAXException

class Itemset(val multiple: Boolean, val hasCopy: Boolean) extends ItemContainer {

  import Itemset._

  def iterateSelectedItems(dataValue: Item.ItemValue[om.NodeInfo]): Iterator[Item] =
    allItemsWithValueIterator(reverse = false) collect {
      case (item, itemValue) if isSelected(multiple, dataValue, itemValue) => item
    }

  // Return the list of items as a JSON tree with hierarchical information
  def asJSON(controlValue: Option[Item.ItemValue[om.NodeInfo]], encode: Boolean, locationData: LocationData): String = {

    val sb = new StringBuilder
    // Array of top-level items
    sb.append("[")
    try {
      visit(null, new ItemsetListener[AnyRef] {

        def startItem(o: AnyRef, item: Item, first: Boolean): Unit = {
          if (! first)
            sb.append(',')

          // Start object
          sb.append("{")

          // Item LHH and value
          sb.append(""""label":"""")
          sb.append(item.javaScriptLabel(locationData))
          item.javaScriptHelp(locationData) foreach { h =>
            sb.append("""","help":"""")
            sb.append(h)
          }
          item.javaScriptHint(locationData) foreach { h =>
            sb.append("""","hint":"""")
            sb.append(h)
          }
          sb.append("""","value":"""")
          sb.append(item.javaScriptValue(encode))
          sb.append('"')

          // Item attributes if any
          val attributes = item.attributes
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
          val itemValueOpt = item.value
          val itemSelected = itemValueOpt exists (itemValue => controlValue exists (isSelected(multiple, _, itemValue)))

          if (itemSelected) {
            sb.append(""","selected":""")
            sb.append(itemSelected.toString)
          }

          // Start array of children items
          if (item.hasChildren)
            sb.append(""","children":[""")
        }

        def endItem(o: AnyRef, item: Item): Unit = {
          // End array of children items
          if (item.hasChildren)
            sb.append(']')

          // End object
          sb.append("}")
        }

        def startLevel(o: AnyRef, item: Item): Unit = ()
        def endLevel(o: AnyRef): Unit = ()
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
    configuration : Configuration,
    controlValue  : Option[Item.ItemValue[om.NodeInfo]],
    locationData  : LocationData
  ): DocumentInfo = {

    val treeBuilder = new TinyBuilder
    val identity = TransformerUtils.getIdentityTransformerHandler(configuration)
    identity.setResult(treeBuilder)

    val ch = new XMLReceiverHelper(identity)

    ch.startDocument()
    ch.startElement("itemset")
    if (hasChildren) {
      visit(null, new ItemsetListener[AnyRef] {

        def startLevel(o: AnyRef, item: Item): Unit =
          ch.startElement("choices")

        def endLevel(o: AnyRef): Unit =
          ch.endElement()

        def startItem(o: AnyRef, item: Item, first: Boolean): Unit = {

          val itemExternalValue = item.externalValue(encode = false)
          val itemSelected      = item.value exists (itemValue => controlValue exists (isSelected(multiple, _, itemValue)))

          val itemAttributes =
            if (itemSelected)
              Array("selected", "true")
            else
              Array[String]()

          // TODO: Item attributes if any

          ch.startElement("item", itemAttributes)

          ch.startElement("label")
          item.label foreach (_.streamAsHTML(ch, locationData))
          ch.endElement()

          item.help foreach { h =>
            ch.startElement("help")
            h.streamAsHTML(ch, locationData)
            ch.endElement()
          }

          item.hint foreach { h =>
            ch.startElement("hint")
            h.streamAsHTML(ch, locationData)
            ch.endElement()
          }

          ch.startElement("value")
          if (itemExternalValue.nonEmpty)
            ch.text(itemExternalValue)
          ch.endElement()
        }

        def endItem(o: AnyRef, item: Item): Unit =
          ch.endElement()
      })
    }
    ch.endElement()
    ch.endDocument()

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