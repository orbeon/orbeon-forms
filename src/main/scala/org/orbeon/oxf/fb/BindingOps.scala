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
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.XFormsConstants.APPEARANCE_QNAME
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.xforms.xbl.BindingDescriptor._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._

trait BindingOps {

    def possibleAppearancesByControlNameAsXML(
        inDoc           : NodeInfo,
        controlName     : String,
        builtinDatatype : String
    ): Array[NodeInfo] = {

        val bindings    = FormBuilder.componentBindings
        val descriptors = getAllRelevantDescriptors(bindings)
        val lang        = FormBuilder.currentLang

        // Compare the local name of the datatypes, assuming that at least the first datatype is a built-in datatype
        def sameDatatypesIgnorePrefix(builtinDatatype: QName, otherDatatype: QName) =
            builtinDatatype == otherDatatype || Model.getVariationTypeOrKeep(builtinDatatype) == otherDatatype

        for {
            controlElem                  ← findControlByName(inDoc, controlName).to[Array]
            originalDatatype             = FormBuilder.DatatypeValidation.fromForm(inDoc, controlName).datatypeQName
            (virtualName, appearanceOpt) = findVirtualNameAndAppearance(
                    elemName    = controlElem.uriQualifiedName,
                    datatype    = originalDatatype,
                    appearances = controlElem attTokens APPEARANCE_QNAME,
                    descriptors = descriptors
                )
            newDatatype                  = Model.qNameForBuiltinTypeName(builtinDatatype, required = false)
            appearanceElem               ← possibleAppearancesWithLabelAsXML(
                    elemName                = virtualName,
                    builtinType             = newDatatype,
                    // If the datatype hasn't changed between original and new, allow using the appearance to select
                    // the current appearance. Otherwise, pass an empty set which means that only the default (empty)
                    // appearance will match. Note that this means that the result might not necessarily have a
                    // selected current appearance.
                    appearancesForSelection = if (sameDatatypesIgnorePrefix(newDatatype, originalDatatype))
                                                  appearanceOpt.to[Set]
                                              else
                                                  Set.empty,
                    lang                    = lang,
                    bindings                = bindings
                )
        } yield
            appearanceElem
    }

    private def possibleAppearancesWithLabelAsXML(
        elemName                : QName,
        builtinType             : QName,
        appearancesForSelection : Set[String],
        lang                    : String,
        bindings                : Seq[NodeInfo]
    ): Array[NodeInfo] = {

        def appearanceMatches(appearanceOpt: Option[String]) = appearanceOpt match {
            case Some(appearance) ⇒ appearancesForSelection contains appearance
            case None             ⇒ appearancesForSelection.isEmpty
        }

        val appearancesXML =
            for {
                (valueOpt, label, icon) ← possibleAppearancesWithLabel(
                        elemName,
                        builtinType,
                        lang,
                        bindings
                    )
            } yield
                <appearance current={appearanceMatches(valueOpt).toString}>
                    <label>{label}</label>
                    <value>{valueOpt.getOrElse("")}</value>
                    <icon>{icon}</icon>
                </appearance>

        appearancesXML map elemToNodeInfo toArray
    }

    // Find the possible appearances and descriptions for the given control with the given datatype. Only return
    // appearances which have metadata.
    //
    // - `None` represents no appearance (default appearance)
    // - `Some(appearance)` represents a specific appearance
    def possibleAppearancesWithLabel(
        elemName : QName,
        datatype : QName,
        lang     : String,
        bindings : Seq[NodeInfo]
    ): Seq[(Option[String], String, String)] = {

        def metadataOpt(bindingOpt: Option[NodeInfo]) =
            bindingOpt.to[List] flatMap bindingMetadata headOption

        possibleAppearancesWithBindings(elemName, datatype, bindings) map {
            case (appearanceOpt, bindingOpt, _) ⇒
                (appearanceOpt, metadataOpt(bindingOpt))
        } collect {
            case (appearanceOpt, Some(metadata)) ⇒

                def findMetadata(elems: Seq[NodeInfo]) = {

                    def fromLang  = elems find (_.attValue("lang") == lang)
                    def fromFirst = elems.headOption

                    fromLang orElse fromFirst map (_.stringValue) flatMap nonEmptyOrNone
                }

                val displayNames = metadata / "*:display-name"
                val icons        = metadata / "*:icon" / "*:small-icon"

                val displayNameOpt = findMetadata(displayNames)
                val icon           = findMetadata(icons) getOrElse "/apps/fr/style/images/silk/plugin.png"

                (appearanceOpt, displayNameOpt, icon)
        } collect {
            case (appearanceOpt, Some(displayName), icon) ⇒
                (appearanceOpt, displayName, icon)
        }
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

            val typeFromDatatype =
                QName.get("type") → ((metadata / "*:datatype" map (_.stringValue) headOption) getOrElse "xs:string")

            val bindAttributes = {
                val asNodeInfo = metadata / "*:templates" / "*:bind" /@ @*
                asNodeInfo map (att ⇒ QName.get(att.getLocalPart, att.getPrefix, att.getURI) →  att.stringValue)
            }

            typeFromDatatype +: bindAttributes
        }

        for {
            (qname, value) ← allAttributes
            if !(qname.getName == "type" && value == "xs:string") // TODO: resolve and don't  literal 'xs:' prefix
        } yield
            attributeInfo(qname, value)
    }

    // From a control element (say <fr:autocomplete>), returns the corresponding <xbl:binding>
    def bindingForControlElementOrEmpty(controlElement: NodeInfo) =
        bindingForControlElement(controlElement, FormBuilder.componentBindings).orNull

    // From a control element (say <fr:autocomplete>), returns the corresponding <xbl:binding>
    def bindingForControlElement(controlElem: NodeInfo, bindings: Seq[NodeInfo]): Option[NodeInfo] = {

        val elemName    = controlElem.uriQualifiedName
        val appearances = controlElem attTokens APPEARANCE_QNAME
        val descriptors = getAllRelevantDescriptors(bindings)

        for {
            descriptor      ← findMostSpecificWithoutDatatype(elemName, appearances, descriptors)
            binding         ← descriptor.binding
        } yield
            binding
    }

    // Finds if a control uses a particular type of editor (say "static-itemset")
    def controlElementHasEditor(controlElem: NodeInfo, editor: String, bindings: Seq[NodeInfo]): Boolean = {

        val editorAttributeValueOpt =
            for {
                binding         ← bindingForControlElement(controlElem, bindings)
                editorAttribute ← (bindingMetadata(binding) / "*:editors" /@ editor).headOption
            } yield
                editorAttribute.stringValue

        editorAttributeValueOpt contains "true"
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
