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

import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.dom.{Document, QName}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.SimpleProcessor
import org.orbeon.oxf.properties.{Properties, PropertySet}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.{Dom4j, TransformerUtils, XMLReceiver}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions.unsafeUnwrapElement
import org.orbeon.scaxon.SimplePath._
import org.orbeon.scaxon.XPath._

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
    val instanceElement   = new DocumentWrapper(readInputAsOrbeonDom(pipelineContext, "instance"), null, XPath.GlobalConfiguration) / *

    val app  = instanceElement / "app"  stringValue
    val form = instanceElement / "form" stringValue

    // Transform and write out the document
    ResourcesPatcher.transform(resourcesDocument, app, form)(Properties.instance.getPropertySet)
    TransformerUtils.writeDom4j(resourcesDocument, xmlReceiver)
  }
}

object ResourcesPatcher {

  def transform(resourcesDocument: Document, app: String, form: String)(implicit properties: PropertySet): Unit = {

    val resourcesElement = new DocumentWrapper(resourcesDocument, null, XPath.GlobalConfiguration).rootElement

    val propertyNames = properties.propertiesStartsWith("oxf.fr.resource" :: app :: form :: Nil mkString ".")

    // In 4.6 summary/detail buttons are at the top level
    def filterPathForBackwardCompatibility(path: Seq[String]) = path take 2 match {
      case Seq("detail" | "summary", "buttons") ⇒ path drop 1
      case _                                    ⇒ path
    }

    val langPathValue = propertyNames.flatMap { propertyName ⇒
      val allTokens            = propertyName.splitTo[List](".")
      val lang                 = allTokens(5)
      val resourceTokens       = allTokens.drop(6)
      val pathString           = filterPathForBackwardCompatibility(resourceTokens).mkString("/")
      // Property name with possible `*` replaced by actual app/form name
      val expandedPropertyName = (List("oxf.fr.resource", app, form, lang) ++ resourceTokens).mkString(".")
      // Had a case where value was null (more details would be useful)
      val value                = properties.getNonBlankString(expandedPropertyName)
      value.map((lang, pathString, _))
    }

    // Return all languages or the language specified if it exists
    // For now we don't support creating new top-level resource elements for new languages.
    def findConcreteLanguages(langOrWildcard: String) = {
      val allLanguages =
        eval(resourcesElement, "resource/@xml:lang/string()").asInstanceOf[Seq[String]]

      val filtered =
        if (langOrWildcard == "*")
          allLanguages
        else
          allLanguages filter (_ == langOrWildcard)

      filtered.distinct // there *shouldn't* be duplicate languages in the source
    }

    def resourceElementsForLang(lang: String) =
      eval(resourcesElement, s"resource[@xml:lang = '$lang']").asInstanceOf[Seq[NodeInfo]] map unsafeUnwrapElement

    // Update or create elements and set values
    for {
      (langOrWildcard, path, value) ← langPathValue.distinct
      lang                          ← findConcreteLanguages(langOrWildcard)
      rootForLang                   ← resourceElementsForLang(lang)
    } locally {
      Dom4j.ensurePath(rootForLang, path split "/" map QName.get).setText(value)
    }
  }
}