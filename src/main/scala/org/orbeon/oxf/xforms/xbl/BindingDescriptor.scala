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

case class BindingAttributeDescriptor(name: QName, predicate: String, value: String)

// Minimum amount of things we need to filter bindings
case class BindingDescriptor(
    elementName : Option[QName],
    datatype    : Option[QName],
    att         : Option[BindingAttributeDescriptor])(
    val binding : Option[NodeInfo] // not part of the case-classiness
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
            val (selectors, ns) = getBindingSelectorsAndNamespaces(binding)
            createDirectToDatatypeMappings(selectors, ns, binding)
        }
        
        (bindings map createOne).foldLeft(Map.empty[QName, BindingDescriptor])(_ ++ _)
    }

    // Find the first direct binding
    def findFirstDirectBinding(selectors: String, ns: Map[String, String]): Option[QName] =
        CSSSelectorParser.parseSelectors(selectors) collectFirst directBindingPF(ns, None) flatMap (_.elementName)

    private def qNameFromElementSelector(selectorOpt: Option[SimpleElementSelector], ns: Map[String, String]) =
        selectorOpt collect {
            case TypeSelector(Some(Some(prefix)), localname) ⇒ QName.get(localname, prefix, ns(prefix))
        }

    // Example: fr|number, xf|textarea
    def directBindingPF(
        ns      : Map[String, String],
        binding : Option[NodeInfo]
    ): PartialFunction[Selector, BindingDescriptor] = {
        case Selector(
                ElementWithFiltersSelector(
                    Some(TypeSelector(Some(Some(prefix)), localname)),
                    Nil),
                Nil) ⇒

            BindingDescriptor(
                Some(QName.get(localname, prefix, ns(prefix))),
                None,
                None
            )(binding)
    }

    // Example: xf|input:xxf-type("xs:decimal")
    private def datatypeBindingPF(
        ns      : Map[String, String],
        binding : Option[NodeInfo]
    ): PartialFunction[Selector, BindingDescriptor] = {
        case Selector(
                ElementWithFiltersSelector(
                    Some(TypeSelector(Some(Some(prefix)), localname)),
                    List(FunctionalPseudoClassFilter("xxf-type", List(StringExpr(datatype))))
                ),
                Nil) ⇒

            BindingDescriptor(
                Some(QName.get(localname, prefix, ns(prefix))),
                nonEmptyOrNone(datatype) map (extractTextValueQName(ns.asJava, _, true)),
                None
            )(binding)
    }

    def attributeBindingPF(
        ns      : Map[String, String],
        binding : Option[NodeInfo]
    ): PartialFunction[Selector, BindingDescriptor] = {
        case Selector(
                ElementWithFiltersSelector(
                    typeSelectorOpt,
                    List(AttributeFilter(None, attName, Some(AttributePredicate(attPredicate, attValue))))
                ),
                Nil) ⇒

            BindingDescriptor(
                qNameFromElementSelector(typeSelectorOpt, ns),
                None,
                Some(BindingAttributeDescriptor(QName.get(attName), attPredicate, attValue))// TODO: QName for attName
            )(binding)
    }

    def getAllDirectAndDatatypeDescriptors(bindings: Seq[NodeInfo]) = {

        def descriptorsForSelectors(selectors: String, ns: Map[String, String], binding: NodeInfo) =
            CSSSelectorParser.parseSelectors(selectors) collect
                directBindingPF(ns, Some(binding)).orElse(datatypeBindingPF(ns, Some(binding)))

        for {
            binding         ← bindings
            (selectors, ns) = getBindingSelectorsAndNamespaces(binding)
            descriptor      ← descriptorsForSelectors(selectors, ns, binding)
        } yield
            descriptor
    }

    private def getBindingSelectorsAndNamespaces(binding: NodeInfo) =
        (binding attValue "element", binding.namespaceMappings.toMap)

    // For selectors that have one or more direct bindings, AND one optional datatype binding, return a map of direct
    // bindings names to the BindingDescriptor for the datatype binding
    private def createDirectToDatatypeMappings(
        selectors : String,
        ns        : Map[String, String],
        binding   : NodeInfo
    ): Map[QName, BindingDescriptor] = {

        val parsed = CSSSelectorParser.parseSelectors(selectors)

        val directBindings =
            parsed collect directBindingPF(ns, Some(binding))

        val datatypeBindings =
            parsed collect datatypeBindingPF(ns, Some(binding))

        val mapping =
            for {
                directBinding        ← directBindings
                firstDatatypeBinding ← datatypeBindings.headOption
                elementName          ← directBinding.elementName
            } yield
                elementName → firstDatatypeBinding

        mapping.toMap
    }

    // For a given control name and datatype
    // NOTE: Again, some assumptions are made. We search first a datatype descriptor, then a direct descriptor.
    def findCurrentBinding(controlName: QName, datatype: QName, descriptors: Seq[BindingDescriptor]): Option[NodeInfo] = {

        val Datatype1 = datatype
        val Datatype2 = Model.getVariationTypeOrKeep(datatype)

        def findDatatypeDescriptor =
            descriptors collectFirst {
                case d @ BindingDescriptor(Some(`controlName`), Some(Datatype1 | Datatype2), None) ⇒ d.binding
            } flatten

        def findDirectDescriptor =
            descriptors collectFirst {
                case d @ BindingDescriptor(Some(`controlName`), None, None) ⇒ d.binding
            } flatten

        findDatatypeDescriptor orElse findDirectDescriptor
    }

    def findDirectBindingForDatatypeBinding(
        controlName : QName,
        datatype    : QName,
        mappings    : Map[QName, BindingDescriptor]
    ): Option[QName] = {

        val Datatype1 = datatype
        val Datatype2 = Model.getVariationTypeOrKeep(datatype)

        mappings collectFirst {
            case (qName, BindingDescriptor(Some(`controlName`), Some(Datatype1 | Datatype2), None)) ⇒ qName
        }
    }
}
