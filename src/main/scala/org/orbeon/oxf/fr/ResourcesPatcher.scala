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

import collection.JavaConverters._
import org.dom4j.{Document, QName, Element}
import org.orbeon.oxf.pipeline.api.{XMLReceiver, PipelineContext}
import org.orbeon.oxf.processor.SimpleProcessor
import org.orbeon.oxf.properties.{PropertySet, Properties}
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.{TransformerUtils, Dom4j}
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._
import scala.annotation.tailrec

// Processor to replace or add resources based on properties
// A property looks like: oxf.fr.resource.*.*.en.detail.labels.save
class ResourcesPatcher extends SimpleProcessor  {

    def generateData(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

        // Read inputs
        val resourcesDocument = readInputAsDOM4J(pipelineContext, "data")
        val instanceElement   = new DocumentWrapper(readInputAsDOM4J(pipelineContext, "instance"), null, XPath.GlobalConfiguration) \ *

        val app  = instanceElement \ "app"  stringValue
        val form = instanceElement \ "form" stringValue

        // Transform and write out the document
        ResourcesPatcher.transform(resourcesDocument, app, form)(Properties.instance.getPropertySet)
        TransformerUtils.writeDom4j(resourcesDocument, xmlReceiver)
    }
}

object ResourcesPatcher {

    def transform(resourcesDocument: Document, app: String, form: String)(implicit properties: PropertySet): Unit = {

        val resourcesElement = new DocumentWrapper(resourcesDocument, null, XPath.GlobalConfiguration) \ *

        val propertyNames = properties.propertiesStartsWith("oxf.fr.resource" :: app :: form :: Nil mkString ".")

        val langPathValue =
            for {
                name   ← propertyNames
                tokens = name split """\."""
                lang   = tokens(5)
                path   = tokens drop 6 mkString "/"
            } yield
                (lang, path, properties.getString(name))

        def findElements(lang: String, path: String): List[Element] = {
            val prefix =
                if (lang == "*")
                    "resource"
                else
                    s"resource[@xml:lang = '$lang']"

            // We know the paths only select elements
            eval(resourcesElement, prefix + (if (path.nonEmpty) "/" + path else "")).asScala.to[List].asInstanceOf[List[NodeInfo]] map unwrapElement
        }


        val results =
            for {
                (lang, path, value) ← langPathValue
                elems               = findElements(lang, path)
            } yield
                (lang, path, value, elems)

        val (existing, nonExisting) = results partition (_._4.nonEmpty)

        // Update existing elements
        existing foreach { case (_, _, value, elems) ⇒
            elems foreach { elem ⇒
                elem.setText(value)
            }
        }

        // Create new elements
        nonExisting foreach {  case (lang, path, value, _) ⇒
            findElements(lang, "") foreach { resourceRoot ⇒
                Dom4j.ensurePath(resourceRoot, path split "/").setText(value)
            }
        }
    }
}