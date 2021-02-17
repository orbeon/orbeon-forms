/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.dom
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.SimpleProcessor
import org.orbeon.oxf.properties.{Properties, PropertySet}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, XPath}
import org.orbeon.oxf.xml.dom.Support
import org.orbeon.oxf.xml.{TransformerUtils, XMLReceiver}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeInfoConversions.unsafeUnwrapElement
import org.orbeon.scaxon.SimplePath._

// Processor to replace or add resources based on properties
//
// An property looks like: oxf.fr.resource.*.*.en.detail.labels.save
//
// NOTE: We used to do this in XSLT, but when it came to implement *adding* missing resources, the level of complexity
// increased too much and readability would have suffered so we rewrote in Scala.
class ResourcesPatcher extends SimpleProcessor  {

  def generateData(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

    // Read inputs
    val resourcesDocument = readInputAsOrbeonDom(pipelineContext, "data")
    val instanceElem      = new DocumentWrapper(readInputAsOrbeonDom(pipelineContext, "instance"), null, XPath.GlobalConfiguration) / *

    val appForm = AppForm(
      instanceElem / "app"  stringValue,
      instanceElem / "form" stringValue
    )

    val langs = CoreCrossPlatformSupport.externalContext.getRequest.getFirstParamAsString("langs") map (_.splitTo[Set](","))

    // Transform and write out the document
    ResourcesPatcher.transform(resourcesDocument, appForm, langs)(Properties.instance.getPropertySet)
    TransformerUtils.writeDom4j(resourcesDocument, xmlReceiver)
  }
}

object ResourcesPatcher {

  private val Prefix   = "oxf.fr.resource"
  private val WildCard = "*"

  def transform(
    resourcesDocument : dom.Document,
    appForm           : AppForm,
    langsOpt             : Option[Set[String]] = None
  )(implicit
    properties        : PropertySet
  ): Unit = {

    // Start by filtering out unwanted languages if specified
    langsOpt foreach { langs =>
      resourcesDocument.getRootElement.elements.filterNot(e => langs(e.attributeValue(XMLNames.XMLLangQName))) foreach
        (_.detach())
    }

    val resourceElems = new DocumentWrapper(resourcesDocument, null, XPath.GlobalConfiguration).rootElement / "resource"
    val propertyNames = properties.propertiesStartsWith((Prefix :: appForm.toList).mkString("."))

    // In 4.6 summary/detail buttons are at the top level
    def filterPathForBackwardCompatibility(path: List[String]): List[String] = path match {
      case ("detail" | "summary") :: "buttons" :: _ => path drop 1
      case _                                        => path
    }

    val langPathValue = propertyNames flatMap { propertyName =>

      val _ :: _ :: _ :: _ :: _ :: lang :: resourceTokens = propertyName.splitTo[List](".")

      // Property name with possible `*` replaced by actual app/form name
      val expandedPropertyName = Prefix :: appForm.toList ::: lang :: resourceTokens mkString "."

      // Had a case where value was null (more details would be useful)
      val value = properties.getNonBlankString(expandedPropertyName)

      value.map((lang, filterPathForBackwardCompatibility(resourceTokens), _))
    }

    // Return all languages or the language specified if it exists
    // For now we don't support creating new top-level resource elements for new languages.
    def findConcreteLanguages(langOrWildcard: String) = {

      val allLanguages =
        resourceElems attValue XMLNames.XMLLangQName

      val filtered =
        if (langOrWildcard == WildCard)
          allLanguages
        else
          allLanguages filter (_ == langOrWildcard)

      filtered.distinct
    }

    def resourceElemsForLang(lang: String) =
      resourceElems filter (_.attValueOpt(XMLNames.XMLLangQName) contains lang) map unsafeUnwrapElement

    // Update or create elements and set values
    for {
      (langOrWildcard, path, value) <- langPathValue.distinct
      lang                          <- findConcreteLanguages(langOrWildcard)
      rootForLang                   <- resourceElemsForLang(lang)
    } locally {
      val elem = Support.ensurePath(rootForLang, path map dom.QName.apply)
      elem.attributeOpt("todo") foreach elem.remove
      elem.setText(value)
    }

    def hasTodo(e: NodeInfo) =
      e.attValueOpt("todo") contains "true"

    def isBracketed(s: String) =
      s.startsWith("[") && s.endsWith("]")

    for {
      e <- resourceElems descendant *
      if ! e.hasChildElement && hasTodo(e) && ! isBracketed(e.stringValue)
      elem = unsafeUnwrapElement(e)
    } locally {
      elem.attributeOpt("todo") foreach elem.remove
      elem.setText(s"[${elem.getText}]")
    }
  }
}