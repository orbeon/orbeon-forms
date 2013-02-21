/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.xbl

import org.orbeon.oxf.properties.{JPropertySet, Properties}
import collection.JavaConverters._
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.dom4j.{QName, Element}
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import scala.collection._
import org.orbeon.oxf.xforms.processor.XFormsFeatures._
import scala.collection.mutable.LinkedHashSet
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.util.ScalaUtils._

// Handle XBL scripts and CSS resources
object XBLResources {

    private val XBLMappingPropertyPrefix = "oxf.xforms.xbl.mapping."

    sealed trait HeadElement
    case class ReferenceElement(src: String) extends HeadElement
    case class InlineElement(text: String)   extends HeadElement

    val EmptyHeadElementSeq = Seq[HeadElement]()

    object HeadElement {
        def apply(e: Element): HeadElement = {

            val href = e.attributeValue("href")
            val src = e.attributeValue("src")
            val resType = e.attributeValue("type")
            val rel = Option(e.attributeValue("rel")) getOrElse "" toLowerCase

            e.getName match {
                case "link" if (href ne null) && ((resType eq null) || resType == "text/css") && rel == "stylesheet" ⇒
                    ReferenceElement(href)
                case "style" if (src ne null) && ((resType eq null) || resType == "text/css") ⇒
                    ReferenceElement(src)
                case "script" if (src ne null) && ((resType eq null) || resType == "text/javascript") ⇒
                    ReferenceElement(src)
                case "style" if (src eq null)  ⇒
                    InlineElement(e.getStringValue)
                case "script" if (src eq null) ⇒
                    InlineElement(e.getStringValue)
                case _ ⇒
                    throw new IllegalArgumentException("Invalid element passed to HeadElement(): " + Dom4jUtils.elementToDebugString(e))
            }
        }
    }

    // All elements ordered in a consistent way: first by QName, then in the order in which they appear in the binding,
    // removing duplicates.
    def orderedHeadElements(bindings: Iterable[AbstractBinding], getHeadElements: AbstractBinding ⇒ Seq[HeadElement]): Iterable[HeadElement] = {
        import org.orbeon.oxf.xml.Dom4j.QNameOrdering
        (bindings.toList sortBy (_.qNameMatch)).flatMap(getHeadElements)(breakOut): mutable.LinkedHashSet[HeadElement] // breakOut to LinkedHashSet
    }

    // E.g. http://orbeon.org/oxf/xml/form-runner → orbeon
    private def automaticMappings: Map[String, String] = {
        val propertySet = Properties.instance.getPropertySet

        val mappingProperties = propertySet.propertiesStartsWith(XBLMappingPropertyPrefix)

        def evaluate(property: JPropertySet.Property) = (
            for {
                propertyName ← mappingProperties
                uri = propertySet.getString(propertyName)
                prefix = propertyName.substring(XBLMappingPropertyPrefix.length)
            } yield
                uri → prefix
        ) toMap

        // Associate result with the property so it won't be computed until properties are reloaded
        mappingProperties.headOption match {
            case Some(firstPropertyName) ⇒
                propertySet.getProperty(firstPropertyName).associatedValue(evaluate)
            case None ⇒
                Map()
        }
    }

    // E.g. fr:tabview → oxf:/xbl/orbeon/tabview/tabview.xbl
    def getAutomaticXBLMappingPath(uri: String, localname: String) =
        automaticMappings.get(uri) flatMap { prefix ⇒
            val path = "/xbl/" + prefix + '/' + localname + '/' + localname + ".xbl"
            if (ResourceManagerWrapper.instance.exists(path)) Some(path) else None
        }

    // Output baseline, remaining, and inline resources
    def outputResources(
            outputElement: (Option[String], Option[String], Option[String]) ⇒ Unit,
            getBuiltin:    Boolean ⇒ Seq[ResourceConfig],
            getXBL:        ⇒ Iterable[HeadElement],
            xblBaseline:   Iterable[String],
            minimal:       Boolean) {

        // For now, actual builtin resources always include the baseline builtin resources
        val builtinBaseline: LinkedHashSet[String] = getBuiltin(false).map(_.getResourcePath(minimal))(breakOut)
        val allBaseline = builtinBaseline ++ xblBaseline

        // Output baseline resources with a CSS class
        allBaseline foreach (s ⇒ outputElement(Some(s), Some("xforms-baseline"), None))

        // This is in the order defined by XBLBindings.orderedHeadElements
        val xbl = getXBL

        val builtinUsed: LinkedHashSet[String] = getBuiltin(true).map(_.getResourcePath(minimal))(breakOut)
        val xblUsed: List[String] = xbl.collect({ case e: ReferenceElement ⇒ e.src })(breakOut)

        // Output remaining resources if any, with no CSS class
        builtinUsed ++ xblUsed -- allBaseline foreach (s ⇒ outputElement(Some(s), None, None))

        // Output inline XBL resources
        xbl collect { case e: InlineElement ⇒ outputElement(None, None, Option(e.text)) }
    }

    // Get all the baseline JS and CSS resources given the oxf.xforms.resources.baseline property
    // The returned resources are in a consistent order of binding QNames then order within the bindings
    def baselineResources =
        XFormsProperties.getResourcesBaseline match {
            case baselineProperty: JPropertySet.Property ⇒

                def evaluate(property: JPropertySet.Property) = {
                    val qNames =
                        stringToSet(property.value.toString) map
                        (Dom4jUtils.extractTextValueQName(property.namespaces.asJava, _, true)) toList

                    def getBinding(qName: QName) = {

                        def getPath(qName: QName) =
                            getAutomaticXBLMappingPath(qName.getNamespaceURI, qName.getName)

                        def fromCache(qName: QName) =
                            getPath(qName) flatMap (BindingCache.get(_, qName, 0))

                        def fromFileThenCache(qName: QName) =
                            getPath(qName) flatMap { path ⇒
                                // Load XBL document
                                val (xblElement, lastModified) = XBLBindings.readXBLResource(path)
                                val bindingsInResource = XBLBindings.extractXBLBindings(Some(path), xblElement, lastModified)

                                // Cache all bindings found so we don't have to re-read them later
                                // NOTE: Typically there is only one binding in each XBL file
                                bindingsInResource foreach (b ⇒ BindingCache.put(path, lastModified, b))

                                // Find if there is a binding with the given name
                                bindingsInResource find (_.qNameMatch == qName)
                            }

                        fromCache(qName) orElse fromFileThenCache(qName)
                    }

                    // All the baseline bindings
                    val bindings = qNames flatMap getBinding

                    val scripts: LinkedHashSet[String] = orderedHeadElements(bindings, _.scripts).collect({ case e: ReferenceElement ⇒ e.src })(breakOut)
                    val styles:  LinkedHashSet[String] = orderedHeadElements(bindings, _.styles) .collect({ case e: ReferenceElement ⇒ e.src })(breakOut)

                    // Return tuple with two sets
                    (scripts, styles)
                }

                // Associate result with the property so it won't be computed until properties are reloaded
                baselineProperty.associatedValue(evaluate)

            case _ ⇒ (collection.Set.empty[String], collection.Set.empty[String])
        }
}
