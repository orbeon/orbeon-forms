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

import XFormsItemUtils.isSelected
import collection.JavaConverters._
import java.lang.{Iterable ⇒ JIterable}
import org.orbeon.dom.Namespace
import org.orbeon.dom.QName
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.saxon.Configuration
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.saxon.tinytree.TinyBuilder
import org.xml.sax.SAXException

/**
 * Represents an itemset.
 */
class Itemset(multiple: Boolean) extends ItemContainer {

  import Itemset._

  // All of this itemset's selected items based on the given instance value
  def jSelectedItems(value: String): JIterable[Item] =
        selectedItems(value).asJava

    def selectedItems(value: String): List[Item] =
        allItemsIterator filter (item ⇒ isSelected(multiple, value, item.value)) toList

  // Return the list of items as a JSON tree with hierarchical information
  def asJSON(controlValue: String, encode: Boolean, locationData: LocationData): String = {
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
          item.javaScriptHelp(locationData) foreach { h ⇒
            sb.append("""","help":"""")
            sb.append(h)
          }
          item.javaScriptHint(locationData) foreach { h ⇒
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
                (name, value) ← attributes
                escapedName   = XFormsUtils.escapeJavaScript(getAttributeName(name))
                escapedValue  = XFormsUtils.escapeJavaScript(value)
              } yield
                s""""$escapedName":"$escapedValue""""

            sb.append(nameValues mkString ",")

            sb.append('}')
          }

          // Handle selection
          val itemValue = Option(item.value) getOrElse ""
          val itemSelected = (itemValue ne null) && isSelected(multiple, controlValue, itemValue)

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

        def startLevel(o: AnyRef, item: Item) = ()
        def endLevel(o: AnyRef) = ()
      })
    } catch {
      case e: SAXException ⇒
        throw new ValidationException("Error while creating itemset tree", e, locationData)
    }
    sb.append("]")

    sb.toString
  }

  // Return the list of items as an XML tree
  def asXML(configuration: Configuration, controlValue: String, locationData: LocationData): DocumentInfo = {
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
          val itemValue = Option(item.value) getOrElse ""
          val itemSelected = (itemValue ne null) && isSelected(multiple, controlValue, itemValue)

          val itemAttributes =
            if (itemSelected)
              Array("selected", "true")
            else
              Array[String]()

          // TODO: Item attributes if any

          ch.startElement("item", itemAttributes)

          ch.startElement("label")
          item.label.streamAsHTML(ch, locationData)
          ch.endElement()

          item.help foreach { h ⇒
            ch.startElement("help")
            h.streamAsHTML(ch, locationData)
            ch.endElement()
          }

          item.hint foreach { h ⇒
            ch.startElement("hint")
            h.streamAsHTML(ch, locationData)
            ch.endElement()
          }

          ch.startElement("value")
          ch.text(itemValue)
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
    if (name.getNamespace == Namespace.EmptyNamespace)
      name.getName
    else if (name.getNamespace == XFormsConstants.XXFORMS_NAMESPACE)
      "xxforms-" + name.getName
    else
      throw new IllegalStateException("Invalid attribute on item: " + name.getName)
}