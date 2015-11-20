/**
 * Copyright (C) 2015 Orbeon, Inc.
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
package org.orbeon.oxf.json

import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xml._
import org.orbeon.saxon.om.NodeInfo
import spray.json._

//
// Functions to convert JSON to XML and back following the XForms 2.0 specification.
//
// The conversion follows the following principles:
//
// - Any JSON document is convertible to XML.
// - However, the opposite is not true, and only XML documents following a very specific pattern
//   can be converted to JSON. In other words the purpose of the conversion rules is to expose JSON
//   to XML processing and not the other way around.
// - XPath expressions which apply to the resulting XML document feel as natural as possible in most
//   cases and can be written just by looking at the original JSON.
//
object Converter {

  // Convert a JSON String to a stream of XML events
  def jsonStringToXML(source: String, receiver: XMLReceiver): Unit =
    jsonToXML(source.parseJson, receiver)

  // Convert a JSON AST to a stream of XML events
  def jsonToXML(ast: JsValue, receiver: XMLReceiver): Unit = {

    import XMLReceiverSupport._

    implicit val rcv = new DeferredXMLReceiverImpl(receiver)

    def processValue(jsValue: JsValue): Unit =
      jsValue match {
        case JsString(v) ⇒
          // TODO: Escaped characters are transformed as necessary; characters and escapes that have no
          // equivalent XML character [...]
          rcv.addAttribute(Symbols.Type, Symbols.String)
          text(v)
        case JsNumber(v) ⇒
          rcv.addAttribute(Symbols.Type, Symbols.Number)
          text(v.toString)
        case JsBoolean(v) ⇒
          rcv.addAttribute(Symbols.Type, Symbols.Boolean)
          text(v.toString)
        case JsNull ⇒
          rcv.addAttribute(Symbols.Type, Symbols.Null)
        case JsObject(fields) ⇒
          rcv.addAttribute(Symbols.Object, Symbols.True)
          fields foreach { case (name, value) ⇒

            val ncName  = SaxonUtils.makeNCName(name, keepFirstIfPossible = true)
            val nameAtt = ncName != name list (Symbols.Name → name)

            def attsForArray = (Symbols.Array → Symbols.True) :: nameAtt

            value match {
              case JsArray(arrayValues) if arrayValues.isEmpty ⇒
                element(ncName, attsForArray)
              case JsArray(arrayValues) ⇒
                val atts = attsForArray
                arrayValues foreach { arrayValue ⇒
                  withElement(ncName, atts) {
                    processValue(arrayValue)
                  }
                }
              case _ ⇒
                withElement(ncName, nameAtt) {
                  processValue(value)
                }
            }
          }
        case JsArray(arrayValues) if arrayValues.isEmpty ⇒
          element(Symbols.Anonymous, List(Symbols.Name → "", Symbols.Array → Symbols.True))
        case JsArray(arrayValues) ⇒
          arrayValues foreach { arrayValue ⇒
            withElement(Symbols.Anonymous, List(Symbols.Name → "", Symbols.Array → Symbols.True)) {
              processValue(arrayValue)
            }
          }
      }

    withDocument {
      withElement(Symbols.JSON) {
        processValue(ast)
      }
    }
  }

  // Convert an XML tree to a JSON String
  def xmlToJsonString(root: NodeInfo, strict: Boolean): String =
    xmlToJson(root, strict).toString

  // Convert an XML tree to a JSON AST
  def xmlToJson(root: NodeInfo, strict: Boolean): JsValue = {

    import org.orbeon.scaxon.XML._

    def isArray (elem: NodeInfo) = (elem attValue    Symbols.Array)  == Symbols.True
    def isObject(elem: NodeInfo) = (elem attValue    Symbols.Object) == Symbols.True
    def typeOpt (elem: NodeInfo) =  elem attValueOpt Symbols.Type
    def elemName(elem: NodeInfo) =  elem attValueOpt Symbols.Name getOrElse elem.localname

    def throwError(s: String) =
      throw new IllegalArgumentException(s)

    def isEmptyArray(elem: NodeInfo) =
      typeOpt(elem).isEmpty && ! isObject(elem) && ! hasChildElement(elem)

    def processElement(elem: NodeInfo): JsValue =
      (typeOpt(elem), isObject(elem)) match {
        case (Some(Symbols.String) , _) ⇒ JsString(elem.stringValue)
        case (Some(Symbols.Number) , _) ⇒ JsNumber(elem.stringValue)
        case (Some(Symbols.Boolean), _) ⇒ JsBoolean(elem.stringValue.toBoolean)
        case (Some(Symbols.Null)   , _) ⇒ JsNull
        case (Some(other)          , _) ⇒ // invalid `type` attribute
          if (strict)
            throwError(s"""unknown datatype `type="$other"`""")
          JsNull
        case (None, true) ⇒ // `object="true"`

          val childrenGroupedByName = elem / * groupByKeepOrder elemName

          val fields =
            childrenGroupedByName map { case (name, elems @ head +: tail) ⇒
              name → {
                if (isArray(head)) {
                  if (strict && ! (elems forall isArray))
                    throwError(s"""all array elements with name $name must have `array="true"`""")

                  if (isEmptyArray(head))
                    JsArray()
                  else
                    JsArray(elems map processElement toVector)
                } else {
                  if (strict && tail.nonEmpty)
                    throwError(s"""only one element with name $name is allowed when there is no `array="true"`""")
                  processElement(head)
                }
              }
            }

          JsObject(fields.toMap)

        case (None, false) ⇒ // must be an anonymous array

          val childrenGroupedByName = elem / * groupByKeepOrder elemName

          childrenGroupedByName.headOption match {
            case Some((name, elems @ head +: tail)) ⇒

              if (strict && name != "")
                throwError("""anonymous array elements must have `name=""`""")
              if (strict && ! (elems forall isArray))
                throwError("""all anonymous array elements must have `array="true"`""")

              if (isEmptyArray(head))
                JsArray()
              else
                JsArray(elems map processElement toVector)

            case None ⇒
              if (strict)
                throwError("""anonymous array is missing child element with `array="true"`""")
              JsNull
          }
      }

    processElement(
      if (isDocument(root))
        root.rootElement
      else if (isElement(root))
        root
      else
        throw new IllegalArgumentException("node must be an element or document")
    )
  }

  private object Symbols {
    val Object    = "object"
    val Array     = "array"
    val String    = "string"
    val Number    = "number"
    val Boolean   = "boolean"
    val Null      = "null"

    val True      = "true"
    val Type      = "type"
    val Name      = "name"
    val JSON      = "json"
    val Anonymous = "_"
  }
}