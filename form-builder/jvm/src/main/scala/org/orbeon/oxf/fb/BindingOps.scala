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

import org.orbeon.dom.QName
import org.orbeon.oxf.fb.XMLNames._
import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.fr.FormRunner.findControlByName
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.NodeInfoFactory._
import org.orbeon.xforms.XFormsNames.APPEARANCE_QNAME
import org.orbeon.oxf.xforms.analysis.model.ModelDefs
import org.orbeon.oxf.xforms.xbl.BindingDescriptor._
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import scala.collection.compat._

trait BindingOps {

  def possibleAppearancesByControlNameAsXML(
    controlName       : String,
    isInitialLoad     : Boolean,
    builtinDatatype   : String,
    desiredAppearance : String)(implicit // relevant only if isInitialLoad == false
    ctx               : FormBuilderDocContext
  ): List[NodeInfo] = {

    val descriptors = getAllRelevantDescriptors(ctx.componentBindings)
    val lang        = FormRunner.currentLang

    for {
      controlElem                  <- findControlByName(ctx.formDefinitionRootElem, controlName).toList
      originalDatatype             = FormBuilder.DatatypeValidation.fromForm(controlName).datatypeQName
      (virtualName, appearanceOpt) = findVirtualNameAndAppearance(
          searchElemName    = controlElem.uriQualifiedName,
          searchDatatype    = originalDatatype,
          searchAppearances = controlElem attTokens APPEARANCE_QNAME,
          descriptors       = descriptors
        )
      newDatatype                  = ModelDefs.qNameForBuiltinTypeName(builtinDatatype, required = false)
      appearanceElem               <- possibleAppearancesWithLabelAsXML(
          elemName                = virtualName,
          builtinType             = newDatatype,
          // Upon initial load, we want to select as current the original appearance. Later, we want to try
          // to keep the current appearance. For example, if dropdown has type `string`, and we change the
          // type to `date`, we don't want the appearance to reset, so we use the passed `desiredAppearance`.
          // If the appearance doesn't match, then no appearance is marked as current. The UI will pick the
          // first appearance listed as current.
          appearancesForSelection = if (isInitialLoad) appearanceOpt.to(Set) else Set(desiredAppearance),
          lang                    = lang,
          bindings                = ctx.componentBindings
        )
    } yield
      appearanceElem
  }

  def possibleAppearancesWithLabelAsXML(
    elemName                : QName,
    builtinType             : QName,
    appearancesForSelection : Set[String],
    lang                    : String,
    bindings                : Seq[NodeInfo]
  ): Array[NodeInfo] = {

    def appearanceMatches(appearanceOpt: Option[String]) = appearanceOpt match {
      case Some(appearance) => appearancesForSelection contains appearance
      case None             => appearancesForSelection.isEmpty
    }

    val appearancesXML =
      for {
        (valueOpt, label, iconPathOpt, iconClasses) <- possibleAppearancesWithLabel(
            elemName,
            builtinType,
            lang,
            bindings
          )
      } yield
        <appearance current={appearanceMatches(valueOpt).toString}>
          <label>{label}</label>
          <value>{valueOpt.getOrElse("")}</value>
          {
            iconPathOpt.toList map { iconPath =>
              <icon-path>{iconPath}</icon-path>
            }
          }
          <icon-class>{iconClasses}</icon-class>
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
  ): Seq[(Option[String], String, Option[String], String)] = {

    def metadataOpt(bindingOpt: Option[NodeInfo]) =
      bindingOpt.to(List) flatMap bindingMetadata headOption

    possibleAppearancesWithBindings(elemName, datatype, bindings) map {
      case (appearanceOpt, bindingOpt, _) =>
        (appearanceOpt, metadataOpt(bindingOpt))
    } collect {
      case (appearanceOpt, Some(metadata)) =>

        def findMetadata(elems: Seq[NodeInfo]) = {

          def fromLang  = elems find (_.attValue("lang") == lang)
          def fromFirst = elems.headOption

          fromLang orElse fromFirst map (_.stringValue) flatMap trimAllToOpt
        }

        // See also toolbox.xml which duplicates some of this logic
        val displayNameOpt           = findMetadata(metadata / FBDisplayNameTest)
        val iconClasses              = findMetadata(metadata / FBIconTest / FBIconClassTest) getOrElse "fa fa-fw fa-puzzle-piece"
        val iconPathOpt              = findMetadata(metadata / FBIconTest / FBSmallIconTest)
        val appearanceDisplayNameOpt = findMetadata(metadata / FBAppearanceDisplayNameTest)

        (appearanceOpt, appearanceDisplayNameOpt orElse displayNameOpt, iconPathOpt, iconClasses)
    } collect {
      case (appearanceOpt, Some(displayName), iconPathOpt, iconClasses) =>
        (appearanceOpt, displayName, iconPathOpt, iconClasses)
    }
  }

  private def bindingMetadata(binding: NodeInfo) =
    binding / FBMetadataTest

  // From an <xbl:binding>, return the view template (say <fr:autocomplete>)
  def findViewTemplate(binding: NodeInfo): Option[NodeInfo] = {
    val metadata = bindingMetadata(binding)
    (((metadata / FBTemplateTest) ++ (metadata / FBTemplatesTest / FBViewTest)) / *).headOption
  }

  def hasViewTemplateSupportElementFor(binding: NodeInfo, name: String): Boolean =
    findViewTemplate(binding).toSeq / name nonEmpty

  // In other words we leave Type and Required and custom MIPs as they are
  // This must match what is done in annotate.xpl
  private val BindTemplateAttributesToNamespace =
    Set(ModelDefs.Relevant, ModelDefs.Readonly, ModelDefs.Constraint, ModelDefs.Calculate, ModelDefs.Default) map (_.aName)

  // From an <xbl:binding>, return all bind attributes
  // They are obtained from the legacy datatype element or from templates/bind.
  def findBindAttributesTemplate(binding: NodeInfo): Seq[NodeInfo] = {

    val metadata            = bindingMetadata(binding)
    val datatypeMetadataOpt = metadata / FBDatatypeTest headOption
    val bindMetadataOpt     = metadata / FBTemplatesTest / FBBindTest headOption

    val allAttributes = {

      val typeFromDatatype =
        for (elem <- datatypeMetadataOpt.to(List))
        yield
          (elem, QName("type"), elem.stringValue)

      val bindAttributes = {
        for {
          elem <- bindMetadataOpt.to(List)
          att  <- elem /@ @*
        } yield
          (elem, QName(att.getLocalPart, att.getPrefix, att.getURI), att.stringValue)
      }

      typeFromDatatype ::: bindAttributes
    }

    allAttributes collect {
      case (_, qname, value) if BindTemplateAttributesToNamespace(qname) =>
        // Some attributes must be prefixed before being inserted into the edited form
        QName(qname.localName, "fb", XMLNames.FB) -> value
      case (elem, qname, value) if !(qname.localName == "type" && elem.resolveQName(value).localName == "string") =>
        // Exclude `type="*:string"`
        qname -> value
    } map {
      case (qname, value) =>
        attributeInfo(qname, value)
    }
  }

  // From a control element (say `<fr:autocomplete>`), returns the corresponding `<xbl:binding>`
  def bindingForControlElement(controlElem: NodeInfo, bindings: Seq[NodeInfo]): Option[NodeInfo] = {

    val elemName    = controlElem.uriQualifiedName
    val appearances = controlElem attTokens APPEARANCE_QNAME
    val descriptors = getAllRelevantDescriptors(bindings)

    for {
      descriptor      <- findMostSpecificWithoutDatatype(elemName, appearances, descriptors)
      binding         <- descriptor.binding
    } yield
      binding
  }

  // Finds if a control uses a particular type of editor (say "static-itemset")
  def controlElementHasEditor(controlElem: NodeInfo, editor: String, bindings: Seq[NodeInfo]): Boolean = {

    val editorAttributeValueOpt =
      for {
        binding         <- bindingForControlElement(controlElem, bindings)
        editorAttribute <- (bindingMetadata(binding) / FBEditorsTest /@ editor).headOption
      } yield
        editorAttribute.stringValue

    editorAttributeValueOpt contains "true"
  }
}
