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
import org.orbeon.css.CSSSelectorParser
import org.orbeon.oxf.xforms.analysis.model.Model

// Minimum amount of things we need to filter bindings
case class BindingDescriptor(
    elementName: QName,
    datatype: Option[QName],
    appearance: Option[String],
    mediatype: Option[String]
)(
    // Optionally store a reference to the binding element, but this is not part of the case-classiness
    val binding: Option[NodeInfo]
)

object BindingDescriptor {

    import CSSSelectorParser._

    // For all bindings, and for selectors that have one or more direct bindings, AND one optional datatype binding,
    // return a map of direct bindings names to the BindingDescriptor for the datatype binding.
    // This assumes there is only a single direct mapping with a given name among all bindings.
    //
    // RFE: Relax those tight assumptions.
    //
    def createDirectToDatatypeMappingsForAllBindings(bindings: Seq[NodeInfo]): Map[QName, BindingDescriptor] = {
        
        def createOne(binding: NodeInfo) = {
            val (selectors, namespaces) = getBindingSelectorsAndNamespaces(binding)
            createDirectToDatatypeMappings(selectors, namespaces, binding)
        }
        
        (bindings map createOne).foldLeft(Map.empty[QName, BindingDescriptor])(_ ++ _)
    }

    // Find the first direct binding
    // NOTE: Used by AbstractBinding
    def findFirstDirectBinding(selectors: String, namespaces: Map[String, String]): Option[QName] =
        CSSSelectorParser.parseSelectors(selectors) collectFirst directBindingPF(namespaces, None) map (_.elementName)

    // Example: fr|number, xf|textarea
    private def directBindingPF(namespaces: Map[String, String], binding: Option[NodeInfo]): PartialFunction[Selector, BindingDescriptor] = {
        case Selector(
                ElementWithFiltersSelector(
                    Some(TypeSelector(Some(Some(prefix)), localname)),
                    Nil),
                Nil) ⇒

            BindingDescriptor(
                QName.get(localname, prefix, namespaces(prefix)),
                None,
                None,
                None
            )(binding)
    }

    // Example: xf|input:xxf-type("xs:decimal")
    private def datatypeBindingPF(namespaces: Map[String, String], binding: Option[NodeInfo]): PartialFunction[Selector, BindingDescriptor] = {
        case Selector(
                ElementWithFiltersSelector(
                    Some(TypeSelector(Some(Some(prefix)), localname)),
                    List(
                        // TODO: Support appearance and mediatype
                        //AttributeFilter(None, "appearance", Some(AttributePredicate("=", appearance))),
                        //AttributeFilter(None, "mediatype", Some(AttributePredicate("=", mediatype))),
                        FunctionalPseudoClassFilter("xxf-type", List(StringExpr(datatype)))
                    )
                ),
                Nil) ⇒

            BindingDescriptor(
                QName.get(localname, prefix, namespaces(prefix)),
                nonEmptyOrNone(datatype) map (extractTextValueQName(namespaces.asJava, _, true)),
                None,
                None
            )(binding)
    }

    def getAllDirectAndDatatypeDescriptors(bindings: Seq[NodeInfo]) = {

        def descriptorsForSelectors(selectors: String, namespaces: Map[String, String], binding: NodeInfo) =
            CSSSelectorParser.parseSelectors(selectors) collect
                directBindingPF(namespaces, Some(binding)).orElse(datatypeBindingPF(namespaces, Some(binding)))

        for {
            binding    ← bindings
            (selectors, namespaces) = getBindingSelectorsAndNamespaces(binding)
            descriptor ← descriptorsForSelectors(selectors, namespaces, binding)
        } yield
            descriptor
    }

    private def getBindingSelectorsAndNamespaces(binding: NodeInfo) =
        (binding attValue "element", binding.namespaceMappings.toMap)

    // For selectors that have one or more direct bindings, AND one optional datatype binding, return a map of direct
    // bindings names to the BindingDescriptor for the datatype binding
    private def createDirectToDatatypeMappings(selectors: String, namespaces: Map[String, String], binding: NodeInfo): Map[QName, BindingDescriptor] = {

        val parsed = CSSSelectorParser.parseSelectors(selectors)

        val directBindings =
            parsed collect directBindingPF(namespaces, Some(binding))

        val datatypeBindings =
            parsed collect datatypeBindingPF(namespaces, Some(binding))

        val mapping =
            for {
                directBinding        ← directBindings
                firstDatatypeBinding ← datatypeBindings.headOption
            } yield
                directBinding.elementName → firstDatatypeBinding

        mapping.toMap
    }

    // For a given control name and datatype
    // NOTE: Again, some assumptions are made. We search first a datatype descriptor, then a direct descriptor.
    def findCurrentBinding(controlName: QName, datatype: QName, bindings: Seq[NodeInfo]): Option[NodeInfo] = {

        val Datatype1 = datatype
        val Datatype2 = Model.getVariationTypeOrKeep(datatype)

        val descriptors = getAllDirectAndDatatypeDescriptors(bindings)

        def findDatatypeDescriptor =
            descriptors collectFirst {
                case d @ BindingDescriptor(`controlName`, Some(Datatype1 | Datatype2), None, None) ⇒ d.binding
            } flatten

        def findDirectDescriptor =
            descriptors collectFirst {
                case d @ BindingDescriptor(`controlName`, None, None, None) ⇒ d.binding
            } flatten

        findDatatypeDescriptor orElse findDirectDescriptor
    }

    def findDirectBindingForDatatypeBinding(controlName: QName, datatype: QName, mappings: Map[QName, BindingDescriptor]): Option[QName] = {

        val Datatype1 = datatype
        val Datatype2 = Model.getVariationTypeOrKeep(datatype)

        mappings collectFirst {
            case (qName, BindingDescriptor(`controlName`, Some(Datatype1 | Datatype2), None, None)) ⇒ qName
        }
    }
}
