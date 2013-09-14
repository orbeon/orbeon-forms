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
package org.orbeon.oxf.xforms.xbl

import org.dom4j.QName
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils._
import scala.collection.JavaConverters._
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.xforms.analysis.model.Model._

case class BindingDescriptor(elementName: QName, datatype: Option[QName], appearance: Option[String], mediatype: Option[String])

object BindingDescriptor {

    import org.orbeon.oxf.xforms.xbl.CSSSelectorParser._

    def parseAllSelectors(bindings: Seq[NodeInfo]): Map[QName, BindingDescriptor] = {

        def parseBindingSelectors(binding: NodeInfo) =
            parseSelectors(binding attValue  "element", binding.namespaceMappings.toMap)

        // Assume there is only a single simple mapping among all bindings
        (bindings map parseBindingSelectors).foldLeft(Map.empty[QName, BindingDescriptor])(_ ++ _)
    }

    def findDirectBinding(selectors: String, namespaces: Map[String, String]): Option[QName] =
        CSSSelectorParser.parseSelectors(selectors) collectFirst {
            case Selector(ElementWithFiltersSelector(Some(TypeSelector(Some(Some(prefix)), localname)), Nil), Nil) ⇒
                QName.get(localname, prefix, namespaces(prefix))
        }

    def parseSelectors(selectors: String, namespaces: Map[String, String]): Map[QName, BindingDescriptor] = {

        val parsed = CSSSelectorParser.parseSelectors(selectors)

        // Example: fr|number
        val directBindings =
            parsed collect {
                case Selector(ElementWithFiltersSelector(Some(TypeSelector(Some(Some(prefix)), localname)), Nil), Nil) ⇒
                    BindingDescriptor(
                        QName.get(localname, prefix, namespaces(prefix)),
                        None,
                        None,
                        None
                    )
            }

        // Example: xf|input:xxf-type("xs:decimal")
        val datatypeBindings =
            parsed collect {
                case Selector(
                        ElementWithFiltersSelector(
                            Some(TypeSelector(Some(Some(prefix)), localname)),
                            List(
                                // TODO: Support appearance and mediatype
                                //AttributeFilter(None, "appearance", Some(("=", appearance))),
                                //AttributeFilter(None, "mediatype", Some(("=", mediatype))),
                                FunctionalPseudoClassFilter("xxf-type", List(StringExpr(datatype)))
                            )
                        ),
                    Nil) ⇒
                    BindingDescriptor(
                        QName.get(localname, prefix, namespaces(prefix)),
                        nonEmptyOrNone(datatype) map (extractTextValueQName(namespaces.asJava, _, true)),
                        None,
                        None
                    )
            }

        val mapping =
            for {
                directBinding        ← directBindings
                firstDatatypeBinding ← datatypeBindings.headOption
            } yield
                directBinding.elementName → firstDatatypeBinding

        mapping.toMap
    }

    def findDirectBinding(controlName: QName, datatype: QName, mappings: Map[QName, BindingDescriptor]): Option[QName] = {

        val Datatype1 = datatype
        val Datatype2 =
            if (XFormsVariationTypeNames(Datatype1.getName))
                if (Datatype1.getNamespaceURI == FormRunner.XF)
                    QName.get(Datatype1.getName, "", FormRunner.XS)
                else if (Datatype1.getNamespaceURI == FormRunner.XS)
                    QName.get(Datatype1.getName, "", FormRunner.XF)
                else
                    Datatype1
            else
                Datatype1

        mappings collectFirst {
            case (qName, BindingDescriptor(`controlName`, Some(Datatype1 | Datatype2), None, None)) ⇒ qName
        }
    }

    def newElementName(currentControlName: QName, oldDatatype: QName, newDatatype: QName, bindings: Seq[NodeInfo]): Option[QName] = {

        val mappings = BindingDescriptor.parseAllSelectors(bindings)

        // The current control name might be a direct binding, in which case we can find its original name
        val originalName = mappings.get(currentControlName) map (_.elementName) getOrElse currentControlName

        // Using the original control name and the new datatype, try to find a new direct binding
        val newName = findDirectBinding(originalName, newDatatype, mappings) getOrElse originalName

        // Only return Some if the name changes
        currentControlName != newName option newName
    }
}
