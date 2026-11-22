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

import org.orbeon.css.CSSSelectorParser
import org.orbeon.css.CSSSelectorParser.{AttributePredicate, ElementWithFiltersSelector, Selector, TypeSelector}
import org.orbeon.dom.{Namespace, QName}
import org.orbeon.oxf.fb.XMLNames.*
import org.orbeon.oxf.fr.{FormRunner, FormRunnerLang, SchemaOps, XMLNames as FRXMLNames}
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.NodeInfoFactory.*
import org.orbeon.oxf.xforms.action.XFormsAPI.{delete, insert, rename, setvalue}
import org.orbeon.oxf.xforms.analysis.model.{MipName, Types}
import org.orbeon.oxf.xforms.xbl.BindingDescriptor.*
import org.orbeon.oxf.xforms.xbl.BindingIndex.DatatypeMatch
import org.orbeon.oxf.xforms.xbl.{BindingAttributeDescriptor, BindingDescriptor, BindingIndex}
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.{Namespaces, XFormsNames}
import org.orbeon.xforms.XFormsNames.{APPEARANCE_QNAME, TYPE_QNAME}
import org.orbeon.xml.NamespaceMapping

import scala.collection.immutable.ListSet
import scala.collection.mutable


trait BindingOps {

  import Private.*

  def possibleAppearancesByControlNameAsXML(
    controlName       : String, // to find bind for namespace resolution
    isInitialLoad     : Boolean,
    controlElemAtts   : NodeInfo,
    newDatatypeOrEmpty: String,
    desiredAppearance : String
  )(implicit
    ctx               : FormBuilderDocContext
  ): List[NodeInfo] = {

    import ctx.bindingIndex

    val lang        = FormRunner.currentLang
    val newDatatype = resolveDatatypeOrEmpty(controlName, newDatatypeOrEmpty)
    val rawAtts     = BindingDescriptor.getAtts(controlElemAtts)

    val incomingAppearanceOpt =
      desiredAppearance.trimAllToOpt.orElse(
        rawAtts.collectFirst { case (APPEARANCE_QNAME, appearance) => appearance.trimAllToOpt }.flatten
      )

    val (virtualName, resolvedAppearanceOpt) =
      findVirtualNameAndAppearance(
        searchElemName = controlElemAtts.uriQualifiedName,
        searchDatatype = newDatatype,
        searchAtts     = updateAttAppearance(rawAtts, incomingAppearanceOpt)
      )

    // Upon initial load, we want to select as current the original resolved appearance. Later, we want to try
    // to keep the current appearance (falling back to any alias appearance implied by `controlElemAtts`). For
    // example, if dropdown has type `string`, and we change the type to `date`, we don't want the appearance to
    // reset, so we use the passed `desiredAppearance`. If the appearance doesn't match, then no appearance is
    // marked as current, and the UI will pick the first appearance listed as current.
    val effectiveAppearanceOpt =
      if (isInitialLoad) resolvedAppearanceOpt else desiredAppearance.trimAllToOpt.orElse(resolvedAppearanceOpt)

    val nonAppearanceAtts =
      updateAttAppearance(rawAtts, None)

    val descriptionOpt =
      findControlDescriptionAsXml(
        elemName = virtualName,
        datatype = newDatatype,
        atts     = updateAttAppearance(nonAppearanceAtts, effectiveAppearanceOpt),
        lang     = lang
      )

    val appearanceElems =
      possibleAppearancesWithLabelAsXML(
        virtualName             = virtualName,
        builtinType             = newDatatype,
        appearancesForSelection = effectiveAppearanceOpt.to(Set),
        atts                    = nonAppearanceAtts,
        lang                    = lang
      )

    descriptionOpt.toList ::: appearanceElems
  }

  def findPossibleControlConversions(
    controlName           : String,
    controlElemAtts       : NodeInfo,
    currentDatatypeOrEmpty: String,
    desiredAppearance     : String,
    lang                  : String,
    includeAllControls    : Boolean = false
  )(implicit
    ctx                   : FormBuilderDocContext
  ): List[NodeInfo] = {

    val currentDatatype = resolveDatatypeOrEmpty(controlName, currentDatatypeOrEmpty)

    findPossibleControlConversions2(
      controlElemAtts,
      currentDatatype,
      desiredAppearance.trimAllToOpt,
      lang,
      includeAllControls
    ).map { conversionResult =>
      val (builtinType, schemaType) =
        conversionResult.datatypeOpt match {
          case Some(dt) if Namespaces.isForBuiltinType(dt) => (dt.localName, "")
          case Some(dt)                                    => ("", dt.qualifiedName)
          case None                                        => ("string", "")
        }

      <conversion current={conversionResult.isCurrent.toString} compatible={conversionResult.compatible.toString}>
        <full-label>{conversionResult.fullLabelOpt.getOrElse("")}</full-label>
        <value>{conversionResult.itemValueOpt.getOrElse(conversionResult.value.uriQualifiedName)}</value>
        <element-name>{conversionResult.elemName.uriQualifiedName}</element-name>
        <element>
          {
            val incomingAtts = BindingOps.filterCommonAtts(BindingDescriptor.getAtts(controlElemAtts)).toList
            val addedQNames  = conversionResult.addAtts.map(_._1)
            val incomingWithoutAddedAndRemoved = incomingAtts.filter { case (qname, _) =>
              ! conversionResult.removeAtts.contains(qname) && ! addedQNames.contains(qname)
            }

            BindingOps.createElemWithAttributes(
              elemName   = conversionResult.elemName,
              attributes = incomingWithoutAddedAndRemoved ++ conversionResult.addAtts
            )
          }
        </element>
        {
          conversionResult.appearanceOpt.toList map { appearance =>
            <appearance>{appearance}</appearance>
          }
        }
        <builtin-type>{builtinType}</builtin-type>
        <schema-type>{schemaType}</schema-type>
        <standard-classes>{conversionResult.standardClasses}</standard-classes>
        {
          conversionResult.addAtts.map {
            case (name, value) =>
              <add-att name={name.uriQualifiedName}>{value}</add-att>
          }
        }
        {
          conversionResult.removeAtts.map { name =>
            <remove-att name={name.uriQualifiedName}/>
          }
        }
        {
          conversionResult.iconPathOpt.toList map { iconPath =>
            <icon-path>{iconPath}</icon-path>
          }
        }
        <icon-class>{conversionResult.iconClasses}</icon-class>
      </conversion>
    }.map(elemToNodeInfo)
  }

  case class ConversionResult(
    fullLabelOpt   : Option[String],
    value          : QName,
    elemName       : QName,
    appearanceOpt  : Option[String],
    datatypeOpt    : Option[QName],
    addAtts        : Set[(QName, String)],
    removeAtts     : Set[QName],
    standardClasses: String = "",
    iconPathOpt    : Option[String],
    iconClasses    : String,
    isCurrent      : Boolean,
    compatible     : Boolean = true,
    itemValueOpt   : Option[String] = None
  )

  def findPossibleControlConversions2(
    originalControlElemAtts: NodeInfo, // `appearance` attribute is ignored
    originalDatatype       : QName,
    originalAppearanceOpt  : Option[String],
    lang                   : String,
    includeAllControls     : Boolean = false
  )(implicit
    ctx                    : FormBuilderDocContext
  ): List[ConversionResult] = {

    import ctx.bindingIndex

    val originalAttsWithoutAppearance =
      updateAttAppearance(getAtts(originalControlElemAtts), None)

    val originalAttsIncludingAppearance =
      updateAttAppearance(originalAttsWithoutAppearance, originalAppearanceOpt)

    val (originalVirtualName, resolvedAppearanceOpt) =
      findVirtualNameAndAppearance(
        searchElemName = originalControlElemAtts.uriQualifiedName,
        searchDatatype = originalDatatype,
        searchAtts     = originalAttsIncludingAppearance
      )

    val effectiveAttsIncludingAppearance =
      updateAttAppearance(originalAttsWithoutAppearance, originalAppearanceOpt.orElse(resolvedAppearanceOpt))

    val datatypeMatch = DatatypeMatch.makeExcludeStringMatch(originalDatatype)

    def resolveDirect(atts: Iterable[(QName, String)], requireConversions: Boolean): Option[(QName, Option[NodeInfo])] =
      for {
        specificDescriptor <- findMostSpecificBindingUseEqualOrToken(
          searchElemName = originalVirtualName,
          datatypeMatch  = datatypeMatch,
          searchAtts     = atts
        )
        relatedDescriptors = findRelatedDescriptors(specificDescriptor)
        directQNames       = BindingOps.findAllDirectInRelated(relatedDescriptors)
        directQName        <- if (requireConversions) directQNames.find(ctx.conversions.contains) else directQNames.headOption
      } yield
        directQName -> specificDescriptor.binding

    val originalDirectOpt =
      resolveDirect(effectiveAttsIncludingAppearance, requireConversions = true)
        .orElse(resolveDirect(originalAttsWithoutAppearance, requireConversions = true))
        .orElse(
          includeAllControls.flatOption(
            resolveDirect(effectiveAttsIncludingAppearance, requireConversions = false)
              .orElse(resolveDirect(originalAttsWithoutAppearance, requireConversions = false))
          )
        )

    val originalNonCommonAtts =
      BindingOps.filterCommonAtts(originalAttsWithoutAppearance).toSet

    def buildConversionResult(
      originalDirectQName  : QName,
      conversionDirectQName: QName,
      compatible           : Boolean
    ): Option[(NodeInfo, ConversionResult)] =
      for {
        conversionDirectDescriptor       <- BindingOps.findDescriptorForDirectName(conversionDirectQName)
        conversionRelatedDescriptors     = findRelatedDescriptors(conversionDirectDescriptor)
        conversionMostSpecificDescriptor <- findMostSpecificStaticBindingInRelated(conversionRelatedDescriptors)
        conversionBindingElem            <- conversionMostSpecificDescriptor.binding
      } yield {

        val viewTemplateOpt =
          FormBuilder.findViewTemplate(conversionBindingElem, forEnclosingSection = false)

        val (conversionStaticElemName, conversionStaticAppearanceOpt, templateAtts, conversionStandardClasses) =
          viewTemplateOpt match {
            case Some(viewTemplate) =>
              (
                viewTemplate.qName,
                viewTemplate.attValueOpt(APPEARANCE_QNAME).flatMap(_.trimAllToOpt),
                BindingOps.filterCommonAtts(getAtts(viewTemplate)).toSet,
                viewTemplate.attValue("class").splitTo[List]().filter(BindingOps.StandardControlClasses).mkString(" ")
              )
            case None =>
              val elemName =
                conversionMostSpecificDescriptor.elementName.getOrElse(throw new IllegalStateException)
              val appearanceOpt =
                BindingDescriptor.appearanceValueOpt(conversionMostSpecificDescriptor.appearances)
              val descriptorAtts =
                conversionMostSpecificDescriptor.otherAtts.collect {
                  case BindingAttributeDescriptor(name, AttributePredicate.Equal(value)) => name -> value
                  case BindingAttributeDescriptor(name, AttributePredicate.Token(value)) => name -> value
                }
              (elemName, appearanceOpt, descriptorAtts, "")
          }

        val templateDatatypeOpt =
          findBindTemplateNameValues(conversionBindingElem, forEnclosingSection = false).collectFirst {
            case (elem, TYPE_QNAME, value) => elem.resolveQName(value)
          }

        val possibleDatatypes =
          findPossibleDatatypes(conversionRelatedDescriptors).toList

        val originalMatchesPossibleDatatype =
          possibleDatatypes.exists { dt =>
            dt == originalDatatype || Types.getVariationTypeOrKeep(dt) == originalDatatype
          }

        def normalizedOriginalDatatype: QName =
          if (Types.isForBuiltinType(originalDatatype))
            Types.qNameForBuiltinTypeName(originalDatatype.localName, required = false)
          else
            originalDatatype

        // Keep the original datatype when compatible with the target control's supported datatypes (e.g. `xs:integer` on
        // `fr:number`), otherwise use the target template's datatype.
        val conversionDatatypeOpt =
          if (templateDatatypeOpt.isDefined) {
            if (originalMatchesPossibleDatatype)
              Some(normalizedOriginalDatatype)
            else
              templateDatatypeOpt
          } else if (viewTemplateOpt.isEmpty && conversionRelatedDescriptors.exists(d => d.datatype.isDefined && ! d.elementName.contains(conversionDirectQName))) {
            if (originalMatchesPossibleDatatype)
              Some(normalizedOriginalDatatype)
            else
              possibleDatatypes.headOption.map { dt =>
                if (Types.isForBuiltinType(dt)) Types.qNameForBuiltinTypeName(dt.localName, required = false) else dt
              }
          } else {
            None
          }

        val conversionMetadata =
          bindingMetadata(conversionBindingElem)
            .headOption
            .toList

        val conversionDisplayNameOpt = findMetadata(lang, conversionMetadata / FBDisplayNameTest)
        val conversionIconClasses    = findMetadata(lang, conversionMetadata / FBIconTest / FBIconClassTest).getOrElse("fa fa-fw fa-puzzle-piece")
        val conversionIconPathOpt    = findMetadata(lang, conversionMetadata / FBIconTest / FBSmallIconTest)

        val attsToAdd    = templateAtts
        val attsToRemove = originalNonCommonAtts.collect { case (name, _) if ! templateAtts.exists(_._1 == name) => name }

        conversionBindingElem -> ConversionResult(
          fullLabelOpt    = conversionDisplayNameOpt,
          value           = conversionDirectQName,
          elemName        = conversionStaticElemName,
          appearanceOpt   = conversionStaticAppearanceOpt,
          datatypeOpt     = conversionDatatypeOpt,
          addAtts         = attsToAdd,
          removeAtts      = attsToRemove,
          standardClasses = conversionStandardClasses,
          iconPathOpt     = conversionIconPathOpt,
          iconClasses     = conversionIconClasses,
          isCurrent       = originalDirectQName == conversionDirectQName,
          compatible      = compatible
        )
      }

    originalDirectOpt match {
      case None =>
        Nil
      case Some((originalDirectQName, originalBindingElemOpt)) =>
        val compatibleQNames =
          ctx.conversions.get(originalDirectQName) match {
            case Some(group) => group.toList
            case None if includeAllControls => List(originalDirectQName)
            case None => Nil
          }

        val compatiblePairs =
          compatibleQNames.flatMap(buildConversionResult(originalDirectQName, _, compatible = true))

        val universalResults =
          if (includeAllControls && originalBindingElemOpt.exists(isEligibleForUniversalConversion)) {
            val seenBindings = compatiblePairs.map(_._1).toSet

            val allBindings =
              bindingIndex.iterateDescriptors.flatMap(_.binding).toList.keepDistinctBy(identity)

            val extraResults =
              for {
                bElem        <- allBindings
                if ! seenBindings.contains(bElem) && isEligibleForUniversalConversion(bElem)
                viewTemplate <- FormBuilder.findViewTemplate(bElem, forEnclosingSection = false).toList
                metadata     <- bindingMetadata(bElem).headOption.toList
                displayName  <- findMetadata(lang, metadata / FBDisplayNameTest).filter(_.nonAllBlank).toList
              } yield {
                val conversionStaticElemName      = viewTemplate.qName
                val conversionStaticAppearanceOpt = viewTemplate.attValueOpt(APPEARANCE_QNAME).flatMap(_.trimAllToOpt)
                val templateAtts                  = BindingOps.filterCommonAtts(getAtts(viewTemplate)).toSet
                val conversionStandardClasses     = viewTemplate.attValue("class").splitTo[List]().filter(BindingOps.StandardControlClasses).mkString(" ")
                val templateDatatypeOpt =
                  findBindTemplateNameValues(bElem, forEnclosingSection = false).collectFirst {
                    case (elem, TYPE_QNAME, value) => elem.resolveQName(value)
                  }
                val conversionIconClasses = findMetadata(lang, metadata / FBIconTest / FBIconClassTest).getOrElse("fa fa-fw fa-puzzle-piece")
                val conversionIconPathOpt = findMetadata(lang, metadata / FBIconTest / FBSmallIconTest)
                val attsToRemove          = originalNonCommonAtts.collect { case (name, _) if ! templateAtts.exists(_._1 == name) => name }
                val uniqueItemValue =
                  s"${conversionStaticElemName.uriQualifiedName}|${conversionStaticAppearanceOpt.getOrElse("")}|${
                    templateAtts.toList.sortBy(_._1.qualifiedName).map { case (k, v) => s"${k.qualifiedName}=$v" }.mkString(",")
                  }"

                ConversionResult(
                  fullLabelOpt    = Some(displayName),
                  value           = conversionStaticElemName,
                  elemName        = conversionStaticElemName,
                  appearanceOpt   = conversionStaticAppearanceOpt,
                  datatypeOpt     = templateDatatypeOpt,
                  addAtts         = templateAtts,
                  removeAtts      = attsToRemove,
                  standardClasses = conversionStandardClasses,
                  iconPathOpt     = conversionIconPathOpt,
                  iconClasses     = conversionIconClasses,
                  isCurrent       = false,
                  compatible      = false,
                  itemValueOpt    = Some(uniqueItemValue)
                )
              }

            extraResults
              .keepDistinctBy(r => (r.fullLabelOpt, r.elemName, r.appearanceOpt, r.addAtts))
              .sortBy(_.fullLabelOpt.getOrElse("").toLowerCase)
          } else {
            Nil
          }

        compatiblePairs.map(_._2) ::: universalResults
    }
  }

  def updatedControlElemAtts(
    controlName            : String,
    controlElemAtts        : NodeInfo,
    previousDatatypeOrEmpty: String,
    newDatatypeOrEmpty     : String,
    appearanceOrEmpty      : String
  )(implicit
    ctx                    : FormBuilderDocContext
  ): NodeInfo = {

    import ctx.bindingIndex

    val currentElemName = controlElemAtts.qName
    val oldDatatype     = resolveDatatypeOrEmpty(controlName, previousDatatypeOrEmpty)
    val newDatatype     = resolveDatatypeOrEmpty(controlName, newDatatypeOrEmpty)
    val nonAppAtts      = updateAttAppearance(BindingDescriptor.getAtts(controlElemAtts), None)

    newElementName(
      oldElemName      = currentElemName,
      oldDatatype      = oldDatatype,
      oldAtts          = nonAppAtts,
      newDatatype      = newDatatype,
      newAppearanceOpt = appearanceOrEmpty.trimAllToOpt
    ) match {
      case Some((newElemName, _)) if newElemName != currentElemName =>
        elemToNodeInfo(
          BindingOps.createElemWithAttributes(
            elemName   = newElemName,
            attributes = nonAppAtts
          )
        )
      case _ =>
        controlElemAtts
    }
  }

  def saveControlElemAtts(
    controlName         : String,
    controlElemAtts     : NodeInfo,
    keepCurrentAtts     : Boolean = false,
    newAppearanceOrEmpty: String  = "",
    newDatatypeOrEmpty  : String  = ""
  )(implicit
    ctx                 : FormBuilderDocContext
  ): Unit = {

    import ctx.bindingIndex

    FormRunner.findControlByName(controlName).foreach { controlElem =>
      val oldBindingElemOpt = bindingForControlElement(controlElem)

      val updatedElem = rename(controlElem, controlElemAtts.qName)

      // Clear `@appearance` here; `writeAlertsAndValidationsAsXML` re-applies `$i/appearances/@selection`.
      delete(updatedElem /@ APPEARANCE_QNAME)

      if (! keepCurrentAtts) {
        val currentNonCommonAttNames =
          BindingOps.filterCommonAtts(BindingDescriptor.getAtts(updatedElem)).map(_._1).toSet

        if (currentNonCommonAttNames.nonEmpty)
          delete((updatedElem /@ @*).filter(att => currentNonCommonAttNames(att.qName)))

        val newAtts =
          BindingOps.filterCommonAtts(BindingDescriptor.getAtts(controlElemAtts)).map {
            case (attQName, attValue) => attributeInfo(attQName, attValue)
          }.toList

        if (newAtts.nonEmpty)
          insert(into = updatedElem, origin = newAtts)
      }

      val newDatatype =
        if (newDatatypeOrEmpty.nonAllBlank)
          resolveDatatypeOrEmpty(controlName, newDatatypeOrEmpty)
        else
          Types.qNameForBuiltinTypeName("string", required = false)
      val newAttsWithAppearance =
        updateAttAppearance(BindingDescriptor.getAtts(updatedElem), newAppearanceOrEmpty.trimAllToOpt)

      val newBindingElemOpt =
        findMostSpecificBindingUseEqualOrToken(
          searchElemName = updatedElem.uriQualifiedName,
          datatypeMatch  = DatatypeMatch.makeExcludeStringMatch(newDatatype),
          searchAtts     = newAttsWithAppearance
        ).flatMap(_.binding)

      if (oldBindingElemOpt != newBindingElemOpt)
        reconcileConvertedControlStructure(
          controlName       = controlName,
          updatedElem       = updatedElem,
          oldBindingElemOpt = oldBindingElemOpt,
          newBindingElemOpt = newBindingElemOpt
        )
    }
  }

  // Return a new element name and appearance for the control if needed.
  // See `BindingDescriptorTest`'s "New element name" test for examples.
  def newElementName(
    oldElemName     : QName,
    oldDatatype     : QName,
    oldAtts         : Iterable[(QName, String)],
    newDatatype     : QName,
    newAppearanceOpt: Option[String]
  )(implicit
    index           : BindingIndex[BindingDescriptor]
  ): Option[(QName, Option[String])] = {

    val oldAppearances =
      oldAtts
        .collectFirst { case (APPEARANCE_QNAME, appearance) => appearance.tokenizeToSet }
        .getOrElse(Set.empty)

    val (virtualName, _) =
      findVirtualNameAndAppearance(oldElemName, oldDatatype, oldAtts)

    // These are the attributes including the new appearance if any
    val newAtts = updateAttAppearance(oldAtts, newAppearanceOpt)

    val newTupleOpt =
      for {
        descriptor                <- findMostSpecificBindingUseEqualOrToken(virtualName, DatatypeMatch.makeExcludeStringMatch(newDatatype), newAtts)
        relatedDescriptors        = findRelatedDescriptors(descriptor)
        (elemName, appearanceOpt) <- findStaticBindingInRelated(virtualName, newAtts, relatedDescriptors)
      } yield
        elemName -> appearanceOpt

    val newTuple =
      newTupleOpt.getOrElse(virtualName -> newAppearanceOpt)

    val newAppearances =
      newTuple._2.toSet.flatMap((_: String).tokenizeToSet)

    (oldElemName != newTuple._1 || oldAppearances != newAppearances) option newTuple
  }

  private[fb] // for tests
  def findControlDescription(
    elemName: QName,
    datatype: QName,
    atts    : Iterable[(QName, String)],
    lang    : String
  )(implicit
    index   : BindingIndex[BindingDescriptor]
  ): Option[String] =
    findMostSpecificBindingUseEqualOrToken(elemName, DatatypeMatch.makeExcludeStringMatch(datatype), atts)
      .flatMap(findDisplayName(_, lang))

  def findDisplayName(descriptor: BindingDescriptor, lang: String) =
    descriptor
      .binding
      .flatMap(bindingMetadata(_).headOption)
      .flatMap(metadata => findMetadata(lang, metadata / FBDisplayNameTest))

  // Find the possible appearances and descriptions for the given control with the given datatype. Only return
  // appearances which have metadata.
  //
  // - `None` represents no appearance (default appearance)
  // - `Some(appearance)` represents a specific appearance
  def possibleAppearancesWithLabel(
    virtualName: QName,
    datatype   : QName,
    atts       : Iterable[(QName, String)],
    lang       : String
  )(implicit
    index   : BindingIndex[BindingDescriptor]
  ): Iterable[(Option[String], Option[String], String, Option[String], String, Boolean)] = {

    def metadataOpt(bindingOpt: Option[NodeInfo]): Option[NodeInfo] =
      bindingOpt.toList flatMap bindingMetadata headOption

    possibleAppearancesWithBindings(virtualName, datatype, atts) map {
      case (appearanceOpt, bindingOpt) =>
        (appearanceOpt, metadataOpt(bindingOpt))
    } collect {
      case (appearanceOpt, Some(metadata)) =>

        // See also toolbox.xml which duplicates some of this logic
        val fullDisplayNameOpt       = findMetadata(lang, metadata / FBDisplayNameTest)
        val iconClasses              = findMetadata(lang, metadata / FBIconTest / FBIconClassTest).getOrElse("fa fa-fw fa-puzzle-piece")
        val iconPathOpt              = findMetadata(lang, metadata / FBIconTest / FBSmallIconTest)
        val appearanceDisplayNameOpt = findMetadata(lang, metadata / FBAppearanceDisplayNameTest)
        val isDeprecated             = metadata.firstChildOpt(FBToolboxTest).flatMap(_.attValueOpt("deprecated")).contains(true.toString)

        (appearanceOpt, fullDisplayNameOpt, appearanceDisplayNameOpt orElse fullDisplayNameOpt, iconPathOpt, iconClasses, isDeprecated)
    } collect {
      case (appearanceOpt, fullDisplayNameOpt, Some(displayName), iconPathOpt, iconClasses, isDeprecated) =>
        (appearanceOpt, fullDisplayNameOpt, displayName, iconPathOpt, iconClasses, isDeprecated)
    }
  }

  // From an `<xbl:binding>`, return the view template (say `<fr:autocomplete>`)
  def findViewTemplate(binding: NodeInfo, forEnclosingSection: Boolean): Option[NodeInfo] = {
    val metadata = bindingMetadata(binding)
    (
      (if (forEnclosingSection) Nil else metadata / FBTemplateTest) ++ // legacy
      (metadata / (if (forEnclosingSection) FBEnclosingSectionTemplatesTest else FBTemplatesTest) / FBViewTest)
    ).firstChildOpt(*)
  }

  def hasViewTemplateSupportElementFor(binding: NodeInfo, name: String): Boolean =
    findViewTemplate(binding, forEnclosingSection = false).toSeq / name nonEmpty

  // From an `<xbl:binding>`, return all bind attributes
  // They are obtained from the legacy `datatype` element or from `templates/bind`.
  // 2026-08-06: so far, this is in use: `type`, `constraint`, `xxf:whitespace`, `xxf:default`, `fr:persist`.
  def findBindAttributesTemplate(binding: NodeInfo, forEnclosingSection: Boolean): Seq[NodeInfo] =
    findBindTemplateNameValues(binding, forEnclosingSection).map { case (_, qname, value) =>
      attributeInfo(qname, value)
    }

  def findBindTemplateNameValues(binding: NodeInfo, forEnclosingSection: Boolean): List[(NodeInfo, QName, String)] = {

    val metadata            = bindingMetadata(binding)
    val datatypeMetadataOpt = if (forEnclosingSection) None else metadata / FBDatatypeTest headOption
    val bindMetadataOpt     = metadata / (if (forEnclosingSection) FBEnclosingSectionTemplatesTest else FBTemplatesTest) / FBBindTest headOption

    val allAttributes = {

      val typeFromDatatype =
        for (elem <- datatypeMetadataOpt.to(List))
        yield
          (elem, TYPE_QNAME, elem.stringValue)

      val bindAttributes =
        for {
          elem <- bindMetadataOpt.to(List)
          att  <- elem /@ @*
        } yield
          (elem, QName(att.getLocalPart, att.getPrefix, att.getURI), att.stringValue)

      typeFromDatatype ::: bindAttributes
    }

    allAttributes.collect {
      case (elem, qname, value) if BindTemplateAttributesToNamespace(qname) =>
        // Some attributes must be prefixed before being inserted into the edited form
        (elem, QName(qname.localName, "fb", XMLNames.FB), value)
      case (elem, qname, value) if !(qname.localName == "type" && elem.resolveQName(value).localName == "string") =>
        // Exclude `type="*:string"`
        (elem, qname, value)
    }
  }

  // From a control element (say `<fr:autocomplete>`), returns the corresponding `<xbl:binding>`
  def bindingForControlElement(
    controlElem: NodeInfo
  )(implicit
    index      : BindingIndex[BindingDescriptor]
  ): Option[NodeInfo] =
    for {
      descriptor <-
        findMostSpecificBindingUseEqualOrToken(
          searchElemName = controlElem.uriQualifiedName,
          datatypeMatch  = DatatypeMatch.Exclude,
          searchAtts     = BindingDescriptor.getAtts(controlElem)
        )
      binding    <- descriptor.binding
    } yield
      binding

  // Finds if a control uses a particular type of editor (say "static-itemset")
  def controlElementHasEditor(
    controlElem: NodeInfo,
    editor     : String
  )(implicit
    index      : BindingIndex[BindingDescriptor]
  ): Boolean = {

    val editorAttributeValueOpt =
      for {
        binding         <- bindingForControlElement(controlElem)
        editorAttribute <- (bindingMetadata(binding) / FBEditorsTest /@ editor).headOption
      } yield
        editorAttribute.stringValue

    editorAttributeValueOpt contains "true"
  }

  def createConversionsMap(implicit index: BindingIndex[BindingDescriptor]): Map[QName, Set[QName]] = {

    // `index.nameOnlySelectors` is prepended in reverse registration order and also includes datatype-only selectors
    // (`xf|input:xxf-type(...)`). Filter to direct-name selectors only (`datatype.isEmpty`) in registration/toolbox
    // order, keeping only the primary (first-declared) direct selector per `<xbl:binding>` (e.g. `fr|range` in
    // `fr|range, xf|range`).
    val allDirectDescriptorsInOrder =
      index.nameOnlySelectors.reverseIterator.collect {
        case (b @ BindingDescriptor(Some(_), None, EmptySet(), EmptySet()), _) => b
      }.toList

    val directDescriptorsInOrder =
      allDirectDescriptorsInOrder.keepDistinctBy(_.binding)

    val bindingToPrimaryDirectQName =
      (for {
        d <- directDescriptorsInOrder
        b <- d.binding
        q <- d.elementName
      } yield b -> q).toMap

    val aliasToPrimaryDirectQName =
      (for {
        d <- allDirectDescriptorsInOrder
        q <- d.elementName
      } yield q -> d.binding.flatMap(bindingToPrimaryDirectQName.get).getOrElse(q)).toMap

    val builder = mutable.ListBuffer[(QName, QName)]()

    directDescriptorsInOrder.foreach { sourceDescriptor =>

      val directName = sourceDescriptor.elementName.get

      sourceDescriptor
        .binding
        .toList
        .flatMap(bindingMetadata(_).headOption)
        .flatMap(_.firstChildOpt(FBConversionsTest))
        .flatMap(e => e.attValue("element").trimAllToOpt.map(_ -> NamespaceMapping(e.namespaceMappings.toMap)))
        .flatMap { case (selectorsString, ns) =>
          CSSSelectorParser.parseSelectors(selectorsString).map(_ -> ns)
        }
        .collect { case (Selector(ElementWithFiltersSelector(Some(ts: TypeSelector), Nil), Seq()), ns) => // direct name only for now
          ts.toQName(ns)
        }
        .flatMap(aliasToPrimaryDirectQName.get)
        .foreach { conversionDirectName =>
          builder += directName -> conversionDirectName
        }
    }

    val rawGroups = BindingOps.createGroups(builder.result())
    val orderMap  = directDescriptorsInOrder.iterator.flatMap(_.elementName).zipWithIndex.toMap
    val compCache = mutable.Map.empty[Set[QName], Set[QName]]

    rawGroups.map { case (k, comp) =>
      val orderedComp =
        compCache.getOrElseUpdate(
          comp,
          comp.toList.sortBy(q => orderMap.getOrElse(q, Int.MaxValue)).to(ListSet)
        )
      k -> orderedComp
    }
  }

  private object Private {

    def resolveDatatypeOrEmpty(
      controlName    : String,
      datatypeOrEmpty: String
    )(implicit
      ctx            : FormBuilderDocContext
    ): QName =
      if (datatypeOrEmpty.contains(":")) {
        val bind            = FormRunner.findBindByName(controlName).get // require the bind just for ns resolution
        val (prefix, local) = SaxonUtils.parseQName(datatypeOrEmpty)
        val uriOpt =
          bind.namespaceMappings.toMap.get(prefix).orElse(
            if (SchemaOps.findSchemaPrefix(bind).contains(prefix))
              SchemaOps.findSchemaNamespace(bind)
            else
              None
          )
        uriOpt match {
          case Some(uri) => QName(local, Namespace(prefix, uri))
          case None      => QName(local, Namespace.EmptyNamespace)
        }
      } else {
        Types.qNameForBuiltinTypeName(datatypeOrEmpty, required = false)
      }

    def bindingMetadata(binding: NodeInfo): NodeColl =
      binding / FBMetadataTest

    def bindingHasEditor(binding: NodeInfo, editor: String): Boolean =
      (bindingMetadata(binding) / FBEditorsTest /@ editor).headOption.exists(_.stringValue == "true")

    val XFormsItemsetTest: Test = Namespaces.XF -> "itemset"
    val XFormsItemElemsTest: Test =
      (Namespaces.XF -> "itemset") ||
      (Namespaces.XF -> "item")    ||
      (Namespaces.XF -> "choices")

    def isEligibleForUniversalConversion(bindingElem: NodeInfo): Boolean =
      bindingMetadata(bindingElem).headOption.exists { metadata =>
        val hasDisplayName      = (metadata / FBDisplayNameTest).nonEmpty
        val toolboxOpt          = metadata.firstChildOpt(FBToolboxTest)
        val isDeprecated        = toolboxOpt.flatMap(_.attValueOpt("deprecated")).contains(true.toString)
        val isHiddenFromToolbox = toolboxOpt.flatMap(_.attValueOpt("show")).contains(false.toString)
        val isSectionTemplate   = bindingElem.attTokens("class").contains("fr-section-component")
        val isNonPersisted      = (metadata / FBTemplatesTest / FBBindTest).exists(_.attValueOpt(FRXMLNames.FRPersistQName).contains("false()"))
        val viewTemplateOpt     = FormBuilder.findViewTemplate(bindingElem, forEnclosingSection = false)
        val isTrigger           = viewTemplateOpt.exists(_.qName == XFormsNames.XFORMS_TRIGGER_QNAME)
        val isStaticAttachment  = viewTemplateOpt.exists(_.attTokens("class").contains("fr-static-attachment"))
        val isValueOrAttachment =
          bindingElem.attValueOpt(XFormsNames.XXBL_MODE_QNAME).forall(_.tokenizeToSet.contains("value")) ||
          viewTemplateOpt.exists(_.attTokens("class").contains("fr-attachment"))
        val hasNestedInstance   = (metadata / FBTemplatesTest / FBInstanceTest / *).nonEmpty

        hasDisplayName &&
          ! isDeprecated &&
          ! isHiddenFromToolbox &&
          ! isSectionTemplate &&
          ! isNonPersisted &&
          viewTemplateOpt.isDefined &&
          ! isTrigger &&
          ! isStaticAttachment &&
          isValueOrAttachment &&
          ! hasNestedInstance
      }

    def reconcileConvertedControlStructure(
      controlName      : String,
      updatedElem      : NodeInfo,
      oldBindingElemOpt: Option[NodeInfo],
      newBindingElemOpt: Option[NodeInfo]
    )(implicit
      ctx              : FormBuilderDocContext
    ): Unit = {

      val oldIsStaticItemset  = oldBindingElemOpt.exists(bindingHasEditor(_, "static-itemset"))
      val newIsStaticItemset  = newBindingElemOpt.exists(bindingHasEditor(_, "static-itemset"))
      val newHasItemHint      = newBindingElemOpt.exists(bindingHasEditor(_, "item-hint"))
      val newViewTemplateOpt  = newBindingElemOpt.flatMap(FormBuilder.findViewTemplate(_, forEnclosingSection = false))
      val newTemplateItemsets = newViewTemplateOpt.toList.flatMap(_ / XFormsItemsetTest)

      // 1. Reconcile `<xf:itemset>` / `<xf:item>` / `<xf:choices>` on the control element
      if (newTemplateItemsets.nonEmpty) {
        if ((updatedElem / XFormsItemsetTest).isEmpty || oldIsStaticItemset != newIsStaticItemset) {
          delete(updatedElem / XFormsItemElemsTest)
          insert(
            into   = updatedElem,
            after  = updatedElem / *,
            origin = newTemplateItemsets.map(copyElementInfoWithoutExtraNamespaces(_, None))
          )
          if (newIsStaticItemset)
            setvalue(updatedElem / XFormsItemsetTest /@ "ref", FormBuilder.buildResourcePointer(controlName, "item", None))
        }
      } else {
        delete(updatedElem / XFormsItemElemsTest)
      }

      // 2. Reconcile `<item>` resources in `fr-form-resources` for all form languages
      if (newIsStaticItemset) {
        for ((formLang, holder) <- FormRunner.findResourceHoldersWithLang(controlName, ctx.resourcesRootElem)) {
          if ((holder / "item").isEmpty) {
            val fbResourceInFormLang  = FormRunnerLang.formResourcesInLang(formLang)
            val originalTemplateItems = fbResourceInFormLang / "template" / "items" / "item"
            val itemsResourceEls =
              if (newHasItemHint)
                originalTemplateItems
              else
                originalTemplateItems.map { item =>
                  elementInfo("item", (item / *).filter(_.localname != "hint"))
                }
            if (itemsResourceEls.nonEmpty)
              insert(
                into   = holder,
                after  = holder / *,
                origin = itemsResourceEls.map(copyElementInfoWithoutExtraNamespaces(_, None))
              )
          }
        }
      } else if (oldIsStaticItemset) {
        for ((_, holder) <- FormRunner.findResourceHoldersWithLang(controlName, ctx.resourcesRootElem))
          delete(holder / "item")
      }

      // 3. Reconcile data holder attributes from `<fb:templates><fb:instance>` while preserving element text
      val oldInstanceAttNames =
        oldBindingElemOpt.toList.flatMap(_ / FBMetadataTest / FBTemplatesTest / FBInstanceTest /@ @*).map(_.qName).toSet
      val newInstanceAtts =
        newBindingElemOpt.toList.flatMap(_ / FBMetadataTest / FBTemplatesTest / FBInstanceTest /@ @*)
      val newInstanceAttNames =
        newInstanceAtts.map(_.qName).toSet
      val attsToRemove =
        oldInstanceAttNames -- newInstanceAttNames

      if (attsToRemove.nonEmpty || newInstanceAtts.nonEmpty) {
        for (dataHolder <- FormRunner.findDataHolders(controlName)) {
          if (attsToRemove.nonEmpty)
            delete((dataHolder /@ @*).filter(a => attsToRemove(a.qName)))
          for (att <- newInstanceAtts) {
            if ((dataHolder /@ @*).forall(_.qName != att.qName))
              insert(into = dataHolder, origin = attributeInfo(att.qName, att.stringValue))
          }
        }
      }
    }

    // In other words we leave Type and Required and custom MIPs except `fr:persist` as they are
    // This must match what is done in annotate.xpl
    val BindTemplateAttributesToNamespace: Set[QName] =
      Set(MipName.Relevant, MipName.Readonly, MipName.Constraint, MipName.Calculate, MipName.Default).map(_.aName) +
        org.orbeon.oxf.fr.XMLNames.FRPersistQName

    def possibleAppearancesWithBindings(
      virtualName: QName,
      datatype   : QName,
      atts       : Iterable[(QName, String)]
    )(implicit
      index   : BindingIndex[BindingDescriptor]
    ): Iterable[(Option[String], Option[NodeInfo])] = {

      val Datatype1 = datatype
      val Datatype2 = Types.getVariationTypeOrKeep(datatype)

      object MatchesAllAttDescsExtractor extends MatchesAllAttDescsExtractor {
        def getAtts: Iterable[(QName, String)] = atts
      }

      val appearancesIt =
        index.iterateDescriptors.collect {
          case
            b @ BindingDescriptor(
              Some(`virtualName`),
              d @ (None | Some(Datatype1 | Datatype2)),
              EmptySet(),
              a @ (EmptySet() | MatchesAllAttDescsExtractor(_))
            ) =>
              (None, b.binding, d.isDefined, a.nonEmpty)
          case
            b @ BindingDescriptor(
              Some(`virtualName`),
              d @ (None | Some(Datatype1 | Datatype2)),
              appearances,
              a @ (EmptySet() | MatchesAllAttDescsExtractor(_))
            ) if BindingDescriptor.appearanceValueOpt(appearances).isDefined =>
              (BindingDescriptor.appearanceValueOpt(appearances), b.binding, d.isDefined, a.nonEmpty)
        }

      // Consider bindings that match an attribute first
      // Keep only the first among identical bindings
      val appearancesToBinding =
        appearancesIt
          .toList
          .keepDistinctBy(_._2)

      if (appearancesToBinding.forall(_._1.isEmpty)) { // no appearance -> no choice (#4558)
        Nil
      } else {

        // Give priority to bindings by attribute
        val filteredByAtt =
          if (appearancesToBinding.exists(_._4))
            appearancesToBinding.filter(_._4)
          else
            appearancesToBinding

        // The datatype is significant -> filter out matches which don't have a datatype
        val filteredByDatatype =
          if (filteredByAtt.exists(_._3))
            filteredByAtt.filter(_._3)
          else
            filteredByAtt

        filteredByDatatype.map(t => t._1 -> t._2)
      }
    }

    def findControlDescriptionAsXml(
      elemName: QName,
      datatype: QName,
      atts    : Iterable[(QName, String)],
      lang    : String
    )(implicit
      index   : BindingIndex[BindingDescriptor]
    ): Option[NodeInfo] =
      findControlDescription(
        elemName,
        datatype,
        atts,
        lang
      ).map { description =>
        <description>{description}</description>
      }.map(elemToNodeInfo)

    def possibleAppearancesWithLabelAsXML(
      virtualName            : QName,
      builtinType            : QName,
      appearancesForSelection: Set[String],
      atts                   : Iterable[(QName, String)],
      lang                   : String
     )(implicit
      index                  : BindingIndex[BindingDescriptor],
    ): List[NodeInfo] = {

      def appearanceMatches(appearanceOpt: Option[String]): Boolean = appearanceOpt match {
        case Some(appearance) =>
          val targetTokens = appearance.tokenizeToSet
          appearancesForSelection.exists(_.tokenizeToSet == targetTokens)
        case None =>
          appearancesForSelection.isEmpty
      }

      val appearancesXML =
        for {
          (valueOpt, fullLabelOpt, label, iconPathOpt, iconClasses, isDeprecated) <-
            possibleAppearancesWithLabel(
              virtualName,
              builtinType,
              atts,
              lang
            )
        } yield
          <appearance current={appearanceMatches(valueOpt).toString} deprecated={isDeprecated.toString}>
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

    def findMetadata(lang: String, elems: Iterable[NodeInfo]): Option[String] = {

      def fromLang  = elems find (_.attValue("lang") == lang)
      def fromFirst = elems.headOption

      fromLang orElse fromFirst map (_.stringValue) flatMap trimAllToOpt
    }

    // This must find a binding that doesn't have a datatype
    def findStaticBindingInRelated(
      searchElemName    : QName,
      searchAtts        : Iterable[(QName, String)], // including `appearance` if any
      relatedDescriptors: Iterable[BindingDescriptor]
    ): Option[(QName, Option[String])] = {

      object AppearancesExtractor extends AppearancesExtractor {
        def getAtts: Iterable[(QName, String)] = searchAtts
        def getFilterAppearance: AttributePredicate => Boolean = EqualOrTokenAttributePredicateFilter
      }

      object MatchesAllAttDescsExtractor extends MatchesAllAttDescsExtractor {
        def getAtts: Iterable[(QName, String)] = searchAtts
      }

      val sortedByAppearanceCount =
        relatedDescriptors.toList.sortBy(- _.appearances.size)

      def findByNameAndAppearance: Option[(QName, Some[String])] =
        sortedByAppearanceCount.collectFirst {
          case
            d @ BindingDescriptor(
              Some(`searchElemName`),
              None,
              AppearancesExtractor(appearance, _),
              EmptySet() | MatchesAllAttDescsExtractor(_)
            ) => searchElemName -> Some(BindingDescriptor.appearanceValueOpt(d.appearances).getOrElse(appearance))
        }

      def findByAppearanceOnly: Option[(QName, Some[String])] =
        sortedByAppearanceCount.collectFirst {
          case
            d @ BindingDescriptor(
              Some(elemName),
              None,
              AppearancesExtractor(appearance, _),
              EmptySet() | MatchesAllAttDescsExtractor(_)
            ) => elemName -> Some(BindingDescriptor.appearanceValueOpt(d.appearances).getOrElse(appearance))
        }

      def findByNameAndAtt: Option[(QName, None.type)] =
        if (searchAtts.exists(_._1 == APPEARANCE_QNAME))
          None
        else
          sortedByAppearanceCount.collectFirst {
            case
              BindingDescriptor(
                Some(`searchElemName`),
                None,
                EmptySet(),
                otherAtts @ MatchesAllAttDescsExtractor(_)
              ) if otherAtts.nonEmpty => searchElemName -> None
          }

      def findDirect: Option[(QName, None.type)] =
        BindingOps.findFirstDirectInRelated(relatedDescriptors).map(_ -> None)

      findByNameAndAppearance orElse
        findByAppearanceOnly  orElse
        findByNameAndAtt      orElse
        findDirect
    }
  }
}

object BindingOps {

  val StandardControlClasses: Set[String] = Set("fr-attachment", "fr-static-attachment")
  private val AutocompleteQName: QName    = QName("autocomplete")

  // `relatedDescriptors` comes from `BindingIndex`, where `nameOnlySelectors` are prepended in reverse declaration order,
  // so reverse to restore declaration order (e.g. `fr|range` before `xf|range`).
  def findAllDirectInRelated(relatedDescriptors: Iterable[BindingDescriptor]): List[QName] =
    relatedDescriptors.collect {
      case
        BindingDescriptor(
          Some(elemName),
          None,
          EmptySet(),
          EmptySet()
        ) => elemName
    }.toList.reverse

  def findFirstDirectInRelated(relatedDescriptors: Iterable[BindingDescriptor]): Option[QName] =
    findAllDirectInRelated(relatedDescriptors).headOption

  def findDescriptorForDirectName(directName: QName)(implicit index: BindingIndex[BindingDescriptor]): Option[BindingDescriptor] =
    index.iterateDescriptors.collectFirst {
      case
        b @ BindingDescriptor(
          Some(`directName`),
          None,
          EmptySet(),
          EmptySet()
        ) => b
    }

  def createGroups(pairs: List[(QName, QName)]): Map[QName, Set[QName]] = {

    val adj = mutable.LinkedHashMap[QName, mutable.LinkedHashSet[QName]]()
    for ((u, v) <- pairs) {
      adj.getOrElseUpdate(u, mutable.LinkedHashSet.empty) += v
      adj.getOrElseUpdate(v, mutable.LinkedHashSet.empty) += u
    }

    val visited    = mutable.Set[QName]()
    val components = mutable.ListBuffer[Set[QName]]()

    for (node <- adj.keys) {
      if (! visited.contains(node)) {
        val component = mutable.LinkedHashSet[QName]()
        def traverse(n: QName): Unit = {
          if (visited.add(n)) {
            component += n
            adj(n).foreach(traverse)
          }
        }
        traverse(node)
        components += component.to(ListSet)
      }
    }

    val result = for {
      comp <- components.result()
      elem <- comp
    } yield elem -> comp

    result.toMap
  }

  def createElemWithAttributes(
    elemName  : QName,
    attributes: Iterable[(QName, String)]
  ): scala.xml.Elem = {

    val elemQualifiedName = SaxonUtils.parseQName(elemName.qualifiedName)
    val emptyElem = <_/>

    val finalMetaData = attributes.foldRight[scala.xml.MetaData](scala.xml.Null) {
      case ((qname, value), acc) =>
        val pre = if (qname.namespace.prefix.isEmpty) None else Some(qname.namespace.prefix)
        scala.xml.Attribute(pre, qname.localName, scala.xml.Text(value), acc)
    }

    val finalScope = attributes.foldLeft(scala.xml.NamespaceBinding(elemQualifiedName._1, elemName.namespace.uri, emptyElem.scope)) {
      case (scope, (qname, _)) if qname.namespace.prefix.nonEmpty && qname.namespace.uri.nonEmpty =>
        if (scope.getURI(qname.namespace.prefix) == qname.namespace.uri) {
          scope
        } else {
          scala.xml.NamespaceBinding(qname.namespace.prefix, qname.namespace.uri, scope)
        }
      case (scope, _) =>
        scope
    }

    emptyElem.copy(
      prefix     = elemQualifiedName._1,
      label      = elemQualifiedName._2,
      attributes = finalMetaData,
      scope      = finalScope
    )
  }

  def filterCommonAtts(atts: Iterable[(QName, String)]): Iterable[(QName, String)] =
    atts.filterNot {
      case (XFormsNames.CLASS_QNAME | XFormsNames.ID_QNAME | XFormsNames.BIND_QNAME | XFormsNames.REF_QNAME | APPEARANCE_QNAME | AutocompleteQName, _) => true
      case (QName((_, org.orbeon.oxf.fr.XMLNames.FRNamespace | org.orbeon.oxf.fb.XMLNames.FBNamespace)), _) => true
      case _ => false
    }
}
