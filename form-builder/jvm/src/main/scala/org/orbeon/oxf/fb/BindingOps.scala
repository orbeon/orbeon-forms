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

import org.orbeon.css.CSSSelectorParser.AttributePredicate
import org.orbeon.dom.QName
import org.orbeon.oxf.fb.XMLNames.*
import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.fr.FormRunner.findControlByName
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.NodeInfoFactory.*
import org.orbeon.oxf.xforms.analysis.model.{MipName, Types}
import org.orbeon.oxf.xforms.xbl.BindingDescriptor.*
import org.orbeon.oxf.xforms.xbl.BindingIndex.DatatypeMatch
import org.orbeon.oxf.xforms.xbl.{BindingDescriptor, BindingIndex}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsNames.APPEARANCE_QNAME


trait BindingOps {

  import Private.*

  def possibleAppearancesByControlNameAsXML(
    controlName       : String,
    isInitialLoad     : Boolean,
    newDatatypeOrEmpty: String,
    desiredAppearance : String // relevant only if `isInitialLoad == false`
  )(implicit
    ctx              : FormBuilderDocContext
  ): List[NodeInfo] = {

    import ctx.bindingIndex

    val lang = FormRunner.currentLang

    val newDatatype =
      if (newDatatypeOrEmpty.contains(":"))
        FormRunner.findBindByName(controlName).get.resolveQName(newDatatypeOrEmpty) // require the bind
      else
        Types.qNameForBuiltinTypeName(newDatatypeOrEmpty, required = false)

    val detailsOpt =
      for {
        controlElem                  <- findControlByName(controlName)
        oldDatatype                  = FormBuilder.DatatypeValidation.fromForm(controlName).datatypeQName
        oldAtts                      = BindingDescriptor.getAtts(controlElem)
        (virtualName, oldAppearanceOpt) =
          findVirtualNameAndAppearance(
            searchElemName = controlElem.uriQualifiedName,
            searchDatatype = oldDatatype,
            searchAtts     = oldAtts
          )
      } yield
        (virtualName, oldAppearanceOpt, oldAtts)

    val descriptionOpt =
      for {
        (virtualName, oldAppearanceOpt, oldAtts) <- detailsOpt
        description <- findControlDescriptionAsXml(
          elemName    = virtualName,
          datatype = newDatatype,
          atts        = updateAttAppearance(oldAtts, if (isInitialLoad) oldAppearanceOpt else Some(desiredAppearance)),
          lang        = lang
        )
      } yield
        description

    val appearanceElems =
      for {
        (virtualName, appearanceOpt, atts) <- detailsOpt.toList
        appearanceElem <- possibleAppearancesWithLabelAsXML(
          virtualName             = virtualName,
          builtinType             = newDatatype,
          // Upon initial load, we want to select as current the original appearance. Later, we want to try
          // to keep the current appearance. For example, if dropdown has type `string`, and we change the
          // type to `date`, we don't want the appearance to reset, so we use the passed `desiredAppearance`.
          // If the appearance doesn't match, then no appearance is marked as current. The UI will pick the
          // first appearance listed as current.
          appearancesForSelection = if (isInitialLoad) appearanceOpt.to(Set) else Set(desiredAppearance),
          atts                    = atts,
          lang                    = lang
        )
      } yield
        appearanceElem

    descriptionOpt.toList ::: appearanceElems
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
      newTupleOpt.getOrElse(virtualName, newAppearanceOpt)

    val oldTuple = (oldElemName, oldAppearances.headOption)

    oldTuple != newTuple option newTuple
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
      .flatMap(_.binding)
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
      (
        (if (forEnclosingSection) Nil else metadata / FBTemplateTest) ++
        (metadata / (if (forEnclosingSection) FBEnclosingSectionTemplatesTest else FBTemplatesTest) / FBViewTest)
      ) / *
    ).headOption
  }

  def hasViewTemplateSupportElementFor(binding: NodeInfo, name: String): Boolean =
    findViewTemplate(binding, forEnclosingSection = false).toSeq / name nonEmpty

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

  private object Private {

    def bindingMetadata(binding: NodeInfo): NodeColl =
      binding / FBMetadataTest

    // In other words we leave Type and Required and custom MIPs as they are
    // This must match what is done in annotate.xpl
    val BindTemplateAttributesToNamespace =
      Set(MipName.Relevant, MipName.Readonly, MipName.Constraint, MipName.Calculate, MipName.Default).map(_.aName)

    def possibleAppearancesWithBindings(
      virtualName: QName,
      datatype   : QName,
      atts       : Iterable[(QName, String)]
    )(implicit
      index   : BindingIndex[BindingDescriptor]
    ): Iterable[(Option[String], Option[NodeInfo])] = {

      val Datatype1 = datatype
      val Datatype2 = Types.getVariationTypeOrKeep(datatype)

      object FirstAttExtractor extends FirstAttExtractor {
        def getAtts: Iterable[(QName, String)] = atts
      }

      val appearancesIt =
        index.iterateDescriptors.collect {
          case
            b @ BindingDescriptor(
            Some(`virtualName`),
            d @ (None | Some(Datatype1 | Datatype2)),
            None,
            a @ (None | FirstAttExtractor(_))
            ) =>
            (None, b.binding, d.isDefined, a.isDefined)
          case
            b @ BindingDescriptor(
            Some(`virtualName`),
            d @ (None | Some(Datatype1 | Datatype2)),
            Some(AttributePredicate.Equal(appearance)),
            a @ (None | FirstAttExtractor(_))
            ) =>
            (Some(appearance), b.binding, d.isDefined, a.isDefined)
          case
            b @ BindingDescriptor(
            Some(`virtualName`),
            d @ (None | Some(Datatype1 | Datatype2)),
            Some(AttributePredicate.Token(appearance)),
            a @ (None | FirstAttExtractor(_))
            ) =>
            (Some(appearance), b.binding, d.isDefined, a.isDefined)
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
        case Some(appearance) => appearancesForSelection contains appearance
        case None             => appearancesForSelection.isEmpty
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
      searchAtts        : Iterable[(QName, String)],
      relatedDescriptors: Iterable[BindingDescriptor]
    ): Option[(QName, Option[String])] = {

      object AppearanceExtractor extends AppearanceExtractor {
        def getAtts: Iterable[(QName, String)] = searchAtts
        def getFilterAppearance: AttributePredicate => Boolean = EqualOrTokenAttributePredicateFilter
      }

      object FirstAttExtractor extends FirstAttExtractor {
        def getAtts: Iterable[(QName, String)] = searchAtts
      }

      def findByNameAndAppearance: Option[(QName, Some[String])] =
        relatedDescriptors.collectFirst {
          case
            BindingDescriptor(
              Some(`searchElemName`),
              None,
              AppearanceExtractor(appearance),
              None | FirstAttExtractor(_)
            ) => searchElemName -> Some(appearance)
        }

      def findByAppearanceOnly: Option[(QName, Some[String])] =
        relatedDescriptors.collectFirst {
          case
            BindingDescriptor(
              Some(elemName),
              None,
              AppearanceExtractor(appearance),
              None | FirstAttExtractor(_)
            ) => elemName -> Some(appearance)
        }

      def findDirect: Option[(QName, None.type)] =
        relatedDescriptors.collectFirst {
          case BindingDescriptor(
              Some(elemName),
              None,
              None,
              None
            ) => elemName -> None
        }

      findByNameAndAppearance orElse
        findByAppearanceOnly  orElse
        findDirect
    }
  }
}
