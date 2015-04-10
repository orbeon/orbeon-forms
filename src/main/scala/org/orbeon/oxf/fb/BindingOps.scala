/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.fb

import org.dom4j.QName
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.xforms.xbl.BindingDescriptor
import BindingDescriptor._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._

trait BindingOps {

    // Return a new element name for the control if needed
    // Examples:
    //
    // - fr:number: decimal → string  ⇒ xf:input
    // - fr:number: string  → string  ⇒ fr:number
    // - xf:input:  string  → string  ⇒ xf:string
    // - xf:input:  string  → decimal ⇒ fr:number
    def newElementName(oldControlName: QName, oldDatatype: QName, newDatatype: QName, bindings: Seq[NodeInfo]): Option[QName] = {

        val directToDatatypeMappings = createDirectToDatatypeMappingsForAllBindings(bindings)

        // The old control name might be a direct binding, in which case we can find the old "virtual" name, that is the
        // name the control would have had if we natively supported datatype bindings
        val oldVirtualNameOpt =
            for {
                BindingDescriptor(elementNameOpt, datatypeOpt, _) ← directToDatatypeMappings.get(oldControlName)
                datatype                                          ← datatypeOpt
                if Set(datatype, Model.getVariationTypeOrKeep(datatype))(oldDatatype)
                elementName                                       ← elementNameOpt
            } yield
                elementName

        val oldVirtualName =
            oldVirtualNameOpt getOrElse oldControlName

        // Using the old virtual control name and the new datatype, try to find a new direct binding
        val newControlName =
            findDirectBindingForDatatypeBinding(oldVirtualName, newDatatype, directToDatatypeMappings) getOrElse oldVirtualName

        // Only return Some if the name changes
        oldControlName != newControlName option newControlName
    }

    private def bindingMetadata(binding: NodeInfo) =
        binding / "*:metadata"

    // From an <xbl:binding>, return the view template (say <fr:autocomplete>)
    def findViewTemplate(binding: NodeInfo): Option[NodeInfo] = {
        val metadata = bindingMetadata(binding)
        (((metadata / "*:template") ++ (metadata / "*:templates" / "*:view")) / *).headOption
    }

    // From an <xbl:binding>, return all bind attributes
    // They are obtained from the legacy datatype element or from templates/bind.
    def findBindAttributesTemplate(binding: NodeInfo): Seq[NodeInfo] = {

        val allAttributes = {
            val metadata = bindingMetadata(binding)
            val typeFromDatatype = QName.get("type") → ((metadata / "*:datatype" map (_.stringValue) headOption) getOrElse "xs:string")
            val bindAttributes = {
                val asNodeInfo = metadata / "*:templates" / "*:bind" /@ @*
                asNodeInfo map (att ⇒ QName.get(att.getLocalPart, att.getPrefix, att.getURI) →  att.stringValue)
            }
            typeFromDatatype +: bindAttributes
        }

        for {
            (qname, value) ← allAttributes
            if !(qname.getName == "type" && value == "xs:string") // TODO: assume literal 'xs:' prefix (should resolve namespace)
        } yield
            attributeInfo(qname, value)
    }

    // From a control element (say <fr:autocomplete>), returns the corresponding <xbl:binding>
    // TODO: Get rid of this and use BindingDescriptor. For this, we need support for @appearance though.
    def bindingForControlElement(controlElement: NodeInfo, bindings: Seq[NodeInfo]) =
        bindings find (b ⇒
            findViewTemplate(b) match {
                case Some(viewTemplate) ⇒
                    viewTemplate.uriQualifiedName              == controlElement.uriQualifiedName &&
                    viewTemplate.att("appearance").stringValue == controlElement.att("appearance").stringValue
                case _ ⇒ false
            })

    // Finds if a control uses a particular type of editor (say "static-itemset")
    // TODO: make `editor` something other than a string
    def controlElementHasEditor(controlElement: NodeInfo, editor: String, bindings: Seq[NodeInfo]): Boolean =
        bindingForControlElement(controlElement, bindings) exists {
            binding ⇒
                val staticItemsetAttribute = (bindingMetadata(binding) / "*:editors" /@ editor).headOption
                staticItemsetAttribute match {
                    case Some(a) ⇒ a.stringValue == "true"
                    case _       ⇒ false
                }
        }

    // Create a new data holder given the new control name, using the instance template if found
    def newDataHolder(controlName: String, binding: NodeInfo): NodeInfo = {

        val instanceTemplate = bindingMetadata(binding) / "*:templates" / "*:instance"

        if (instanceTemplate.nonEmpty)
            elementInfo(controlName, (instanceTemplate.head /@ @*) ++ (instanceTemplate / *))
        else
            elementInfo(controlName)
    }
}
