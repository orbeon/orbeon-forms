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
import org.orbeon.oxf.xforms.analysis.model.MipName
import org.orbeon.oxf.xforms.xbl.BindingDescriptor
import org.orbeon.oxf.xforms.xbl.BindingDescriptor._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsNames.APPEARANCE_QNAME

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

    val detailsOpt =
      for {
        controlElem                  <- findControlByName(controlName)
        originalDatatype             = FormBuilder.DatatypeValidation.fromForm(controlName).datatypeQName
        (virtualName, appearanceOpt) = findVirtualNameAndAppearance(
            searchElemName    = controlElem.uriQualifiedName,
            searchDatatype    = originalDatatype,
            searchAppearances = controlElem attTokens APPEARANCE_QNAME,
            descriptors       = descriptors
          )
        newDatatype                  = MipName.qNameForBuiltinTypeName(builtinDatatype, required = false)
      } yield
        (virtualName, appearanceOpt, newDatatype)

    val descriptionOpt =
      for {
        (virtualName, appearanceOpt, newDatatype) <- detailsOpt
        description <- findControlDescriptionAsXml(
          elemName                = virtualName,
          builtinType             = newDatatype,
          appearancesForSelection = if (isInitialLoad) appearanceOpt.to(Set) else Set(desiredAppearance),
          lang                    = lang,
          descriptors             = descriptors
        )
      } yield
        description

    val appearanceElems =
      for {
        (virtualName, appearanceOpt, newDatatype) <- detailsOpt.toList
        appearanceElem                            <- possibleAppearancesWithLabelAsXML(
          elemName                = virtualName,
          builtinType             = newDatatype,
          // Upon initial load, we want to select as current the original appearance. Later, we want to try
          // to keep the current appearance. For example, if dropdown has type `string`, and we change the
          // type to `date`, we don't want the appearance to reset, so we use the passed `desiredAppearance`.
          // If the appearance doesn't match, then no appearance is marked as current. The UI will pick the
          // first appearance listed as current.
          appearancesForSelection = if (isInitialLoad) appearanceOpt.to(Set) else Set(desiredAppearance),
          lang                    = lang,
          descriptors             = descriptors
        )
      } yield
        appearanceElem

    descriptionOpt.toList ::: appearanceElems
  }

  private def findControlDescription(
    elemName                : QName,
    builtinType             : QName,
    appearancesForSelection : Set[String],
    lang                    : String,
    descriptors             : Iterable[BindingDescriptor]
  ): Option[String] =
    findMostSpecificMaybeWithDatatype(elemName, builtinType, appearancesForSelection, descriptors)
      .flatMap(_.binding)
      .flatMap(bindingMetadata(_).headOption)
      .flatMap(metadata => findMetadata(lang, metadata / FBDisplayNameTest))

  private def findControlDescriptionAsXml(
    elemName                : QName,
    builtinType             : QName,
    appearancesForSelection : Set[String],
    lang                    : String,
    descriptors             : Iterable[BindingDescriptor]
  ): Option[NodeInfo] =
    findControlDescription(
      elemName,
      builtinType,
      appearancesForSelection,
      lang,
      descriptors
    ).map { description =>
      <description>{description}</description>
    }.map(elemToNodeInfo)

  private def possibleAppearancesWithLabelAsXML(
    elemName                : QName,
    builtinType             : QName,
    appearancesForSelection : Set[String],
    lang                    : String,
    descriptors             : Iterable[BindingDescriptor]
  ): List[NodeInfo] = {

    def appearanceMatches(appearanceOpt: Option[String]) = appearanceOpt match {
      case Some(appearance) => appearancesForSelection contains appearance
      case None             => appearancesForSelection.isEmpty
    }

    val appearancesXML =
      for {
        (valueOpt, fullLabelOpt, label, iconPathOpt, iconClasses) <- possibleAppearancesWithLabel(
            elemName,
            builtinType,
            lang,
            descriptors
          )
      } yield
        <appearance current={appearanceMatches(valueOpt).toString}>
          <full-label>{fullLabelOpt.getOrElse("")}</full-label>
          <label>{label}</label>
          <value>{valueOpt.getOrElse("")}</value>
          {
            iconPathOpt.toList map { iconPath =>
              <icon-path>{iconPath}</icon-path>
            }
          }
          <icon-class>{iconClasses}</icon-class>
        </appearance>

    appearancesXML.map(elemToNodeInfo).toList
  }

  private def findMetadata(lang: String, elems: Iterable[NodeInfo]): Option[String] = {

    def fromLang  = elems find (_.attValue("lang") == lang)
    def fromFirst = elems.headOption

    fromLang orElse fromFirst map (_.stringValue) flatMap trimAllToOpt
  }

  // Find the possible appearances and descriptions for the given control with the given datatype. Only return
  // appearances which have metadata.
  //
  // - `None` represents no appearance (default appearance)
  // - `Some(appearance)` represents a specific appearance
  def possibleAppearancesWithLabel(
    elemName   : QName,
    datatype   : QName,
    lang       : String,
    descriptors: Iterable[BindingDescriptor]
  ): Iterable[(Option[String], Option[String], String, Option[String], String)] = {

    def metadataOpt(bindingOpt: Option[NodeInfo]): Option[NodeInfo] =
      bindingOpt.toList flatMap bindingMetadata headOption

    possibleAppearancesWithBindings(elemName, datatype, descriptors) map {
      case (appearanceOpt, bindingOpt, _) =>
        (appearanceOpt, metadataOpt(bindingOpt))
    } collect {
      case (appearanceOpt, Some(metadata)) =>

        // See also toolbox.xml which duplicates some of this logic
        val fullDisplayNameOpt       = findMetadata(lang, metadata / FBDisplayNameTest)
        val iconClasses              = findMetadata(lang, metadata / FBIconTest / FBIconClassTest) getOrElse "fa fa-fw fa-puzzle-piece"
        val iconPathOpt              = findMetadata(lang, metadata / FBIconTest / FBSmallIconTest)
        val appearanceDisplayNameOpt = findMetadata(lang, metadata / FBAppearanceDisplayNameTest)

        (appearanceOpt, fullDisplayNameOpt, appearanceDisplayNameOpt orElse fullDisplayNameOpt, iconPathOpt, iconClasses)
    } collect {
      case (appearanceOpt, fullDisplayNameOpt, Some(displayName), iconPathOpt, iconClasses) =>
        (appearanceOpt, fullDisplayNameOpt, displayName, iconPathOpt, iconClasses)
    }
  }

  private def bindingMetadata(binding: NodeInfo): Seq[NodeInfo] =
    binding / FBMetadataTest

  // From an `<xbl:binding>`, return the view template (say `<fr:autocomplete>`)
  def findViewTemplate(binding: NodeInfo, forEnclosingSection: Boolean): Option[NodeInfo] = {
    val metadata = bindingMetadata(binding)
    (
      (
        (if (forEnclosingSection) Nil else metadata / FBTemplateTest) ++
        (metadata / (if (forEnclosingSection) FBEnclosingSectionTemplatesTest else FBTemplatesTest) / FBViewTest)
      ) / *
    ).headOption
  }

  def hasViewTemplateSupportElementFor(binding: NodeInfo, name: String): Boolean =
    findViewTemplate(binding, forEnclosingSection = false).toSeq / name nonEmpty

  // In other words we leave Type and Required and custom MIPs as they are
  // This must match what is done in annotate.xpl
  private val BindTemplateAttributesToNamespace =
    Set(MipName.Relevant, MipName.Readonly, MipName.Constraint, MipName.Calculate, MipName.Default).map(_.aName)

  // From an `<xbl:binding>`, return all bind attributes
  // They are obtained from the legacy `datatype` element or from `templates/bind`.
  def findBindAttributesTemplate(binding: NodeInfo, forEnclosingSection: Boolean): Seq[NodeInfo] = {

    val metadata            = bindingMetadata(binding)
    val datatypeMetadataOpt = if (forEnclosingSection) None else metadata / FBDatatypeTest headOption
    val bindMetadataOpt     = metadata / (if (forEnclosingSection) FBEnclosingSectionTemplatesTest else FBTemplatesTest) / FBBindTest headOption

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
