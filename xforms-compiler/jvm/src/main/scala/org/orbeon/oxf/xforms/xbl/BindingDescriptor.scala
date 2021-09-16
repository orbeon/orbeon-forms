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

import org.orbeon.css.CSSSelectorParser
import org.orbeon.css.CSSSelectorParser.AttributePredicate
import org.orbeon.dom.QName
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.analysis.model.ModelDefs
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.oxf.xml.dom.Extensions
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsNames.{APPEARANCE_QNAME, XFORMS_STRING_QNAME}
import org.orbeon.xml.NamespaceMapping

import scala.collection.compat._

// CSS selectors can be very complex but we only support a small subset of them for the purpose of binding controls to
// elements. Namely, we can bind:
//
// - by element name only
// - by element name and datatype (with the `xxf:type()` )
// - by element name and a single `appearance` attribute
// - by a single `appearance` attribute
// - by element name, a datatype, and a single `appearance` attribute
//
// `BindingDescriptor` is a minimal CSS selector descriptor able to hold the combinations above. It can in fact hold
// more than that, such as:
//
// - binding by datatype only
// - binding via attributes which are not `appearance`
//
// But those are not used or supported as of 2020-01-10.
case class BindingDescriptor(
  elementName : Option[QName],
  datatype    : Option[QName],
  att         : Option[BindingAttributeDescriptor])(
  val binding : Option[NodeInfo] // not part of the case-classiness
)

// Represent a single attribute biding
case class BindingAttributeDescriptor(name: QName, predicate: AttributePredicate)

object BindingDescriptor {

  import CSSSelectorParser._
  import Private._

  // Return a new element name and appearance for the control if needed.
  // See `BindingDescriptorTest.testNewElementName()` for examples.
  def newElementName(
    oldElemName      : QName,
    oldDatatype      : QName,
    oldAppearances   : Set[String],
    newDatatype      : QName,
    newAppearanceOpt : Option[String],
    bindings         : Iterable[NodeInfo]
  ): Option[(QName, Option[String])] = {

    val descriptors = getAllRelevantDescriptors(bindings)

    val (virtualName, _) =
      findVirtualNameAndAppearance(oldElemName, oldDatatype, oldAppearances, descriptors)

    val newTupleOpt =
      for {
        descriptor                <- findMostSpecificMaybeWithDatatype(virtualName, newDatatype, newAppearanceOpt.to(Set), descriptors)
        relatedDescriptors        = findRelatedDescriptors(descriptor, descriptors)
        (elemName, appearanceOpt) <- findStaticBindingInRelated(virtualName, newAppearanceOpt.to(Set), relatedDescriptors)
      } yield
        elemName -> appearanceOpt

    val newTuple =
      newTupleOpt getOrElse (virtualName, newAppearanceOpt)

    val oldTuple = (oldElemName, oldAppearances.headOption)

    oldTuple != newTuple option newTuple
  }

  // Find the virtual name and appearance for the control given its element name, datatype, and appearances.
  // See `BindingDescriptorTest` for examples.
  //
  // The virtual name is the name the control would have if we natively supported datatype bindings. We don't support
  // support them because datatypes can change dynamically at runtime and that is a big change, see:
  // https://github.com/orbeon/orbeon-forms/issues/1248
  def findVirtualNameAndAppearance(
    searchElemName    : QName,
    searchDatatype    : QName,
    searchAppearances : Set[String],
    descriptors       : Iterable[BindingDescriptor]
  ): (QName, Option[String]) = {

    val virtualNameAndAppearanceOpt =
      for {
        descriptor                                <- findMostSpecificWithoutDatatype(searchElemName, searchAppearances, descriptors)
        if descriptor.att.isEmpty // only a direct binding can be an alias for another related binding
        relatedDescriptors                        = findRelatedDescriptors(descriptor, descriptors)
        BindingDescriptor(elemNameOpt, _, attOpt) <- findRelatedVaryNameAndAppearance(searchDatatype, relatedDescriptors)
        elemName                                  <- elemNameOpt
      } yield
        (
          elemName,
          attOpt collect {
            case BindingAttributeDescriptor(APPEARANCE_QNAME, AttributePredicate.Equal(value)) => value
            case BindingAttributeDescriptor(APPEARANCE_QNAME, AttributePredicate.Token(value)) => value
          }
        )

    virtualNameAndAppearanceOpt getOrElse (searchElemName, searchAppearances.headOption) // ASSUMPTION: Take first appearance.
  }

  def possibleAppearancesWithBindings(
    elemName : QName,
    datatype : QName,
    bindings : Iterable[NodeInfo]
  ): Iterable[(Option[String], Option[NodeInfo], Boolean)] = {

    val Datatype1 = datatype
    val Datatype2 = ModelDefs.getVariationTypeOrKeep(datatype)

    val appearancesToBinding =
      getAllRelevantDescriptors(bindings) collect {
        case b @ BindingDescriptor(
            Some(`elemName`),
            d @ (None | Some(Datatype1 | Datatype2)),
            None
          ) =>
          (None, b.binding, d.isDefined)
        case b @ BindingDescriptor(
            Some(`elemName`), // None | would also match raw attribute selectors
            d @ (None | Some(Datatype1 | Datatype2)),
            Some(BindingAttributeDescriptor(APPEARANCE_QNAME, AttributePredicate.Equal(attValue)))
          ) =>
          (Some(attValue), b.binding, d.isDefined)
        case b @ BindingDescriptor(
            Some(`elemName`), // None | would also match raw attribute selectors
            d @ (None | Some(Datatype1 | Datatype2)),
            Some(BindingAttributeDescriptor(APPEARANCE_QNAME, AttributePredicate.Token(attValue)))
          ) =>
          (Some(attValue), b.binding, d.isDefined)
      }

    if (appearancesToBinding forall (_._1.isEmpty)) // no appearance -> no choice (#4558)
      Nil
    else if (appearancesToBinding exists (_._3))    // datatype is significant -> filter out matches which don't have a datatype
      appearancesToBinding filter (_._3)
    else
      appearancesToBinding
  }

  // Example: `fr|number`, `xf|textarea`
  def directBindingPF(
    ns      : NamespaceMapping,
    binding : Option[NodeInfo]
  ): PartialFunction[Selector, BindingDescriptor] = {
    case Selector(
        ElementWithFiltersSelector(
          Some(TypeSelector(Some(Some(prefix)), localname)),
          Nil),
        Nil) =>

      BindingDescriptor(
        Some(QName(localname, prefix, ns.mapping(prefix))),
        None,
        None
      )(binding)
  }

  // Examples:
  //
  // - `xf:select1[appearance ~= full]`
  // - `[appearance ~= character-counter]`
  def attributeBindingPF(
    ns      : NamespaceMapping,
    binding : Option[NodeInfo]
  ): PartialFunction[Selector, BindingDescriptor] = {
    case Selector(
        ElementWithFiltersSelector(
          typeSelectorOpt,
          List(AttributeFilter(None, attName, attPredicate))
        ),
        Nil) =>

      BindingDescriptor(
        qNameFromElementSelector(typeSelectorOpt, ns),
        None,
        Some(BindingAttributeDescriptor(QName(attName), attPredicate))// TODO: QName for attName
      )(binding)
  }

  def getAllRelevantDescriptors(bindings: Iterable[NodeInfo]): Iterable[BindingDescriptor] =
    getAllSelectorsWithPF(
      bindings,
      (ns, binding) =>
        directBindingPF              (ns, Some(binding)) orElse
        datatypeBindingPF            (ns, Some(binding)) orElse
        attributeBindingPF           (ns, Some(binding)) orElse
        datatypeAndAttributeBindingPF(ns, Some(binding))
    )

  def findMostSpecificWithoutDatatype(
    searchElemName    : QName,
    searchAppearances : Set[String],
    searchDescriptors : Iterable[BindingDescriptor]
  ): Option[BindingDescriptor] = {

    def findByNameAndAppearance =
      searchDescriptors collectFirst {
        case descriptor @ BindingDescriptor(
            Some(`searchElemName`),
            None,
            Some(BindingAttributeDescriptor(APPEARANCE_QNAME, AttributePredicate.Equal(appearance)))
          ) if searchAppearances(appearance) => descriptor
        case descriptor @ BindingDescriptor(
            Some(`searchElemName`),
            None,
            Some(BindingAttributeDescriptor(APPEARANCE_QNAME, AttributePredicate.Token(appearance)))
          ) if searchAppearances(appearance) => descriptor
      }

    def findByNameOnly =
      searchDescriptors collectFirst {
        case descriptor @ BindingDescriptor(
            Some(`searchElemName`),
            None,
            None
          ) => descriptor
      }

    findByNameAndAppearance orElse findByNameOnly
  }

  def findMostSpecificMaybeWithDatatype(
    searchElemName    : QName,
    searchDatatype    : QName,
    searchAppearances : Set[String],
    descriptors       : Iterable[BindingDescriptor]
  ): Option[BindingDescriptor] =
    if (StringQNames(searchDatatype))
      findMostSpecificWithoutDatatype(searchElemName, searchAppearances, descriptors)
    else
      findMostSpecificWithDatatype(searchElemName, searchDatatype, searchAppearances, descriptors) orElse
        findMostSpecificWithoutDatatype(searchElemName, searchAppearances, descriptors)

  private object Private {

    // The `xs:string` and `xf:string` types are default types and considered the same as no type specification
    val StringQNames: Set[QName] = Set(XMLConstants.XS_STRING_QNAME, XFORMS_STRING_QNAME)

    def findRelatedDescriptors(
        searchDescriptor : BindingDescriptor,
        descriptors      : Iterable[BindingDescriptor]
    ): Iterable[BindingDescriptor] =
      descriptors filter (d => d.binding == searchDescriptor.binding)

    def qNameFromElementSelector(selectorOpt: Option[SimpleElementSelector], ns: NamespaceMapping): Option[QName] =
      selectorOpt collect {
        case TypeSelector(Some(Some(prefix)), localname) => QName(localname, prefix, ns.mapping(prefix))
      }

    // Example: `xf|input:xxf-type("xs:decimal")`
    def datatypeBindingPF(
      ns      : NamespaceMapping,
      binding : Option[NodeInfo]
    ): PartialFunction[Selector, BindingDescriptor] = {
      case Selector(
          ElementWithFiltersSelector(
            Some(TypeSelector(Some(Some(prefix)), localname)),
            List(FunctionalPseudoClassFilter("xxf-type", List(StringExpr(datatype))))
          ),
          Nil) =>

        BindingDescriptor(
          Some(QName(localname, prefix, ns.mapping(prefix))),
          datatype.trimAllToOpt flatMap (Extensions.resolveQName(ns.mapping.get, _, unprefixedIsNoNamespace = true)) filterNot StringQNames,
          None
        )(binding)
    }

    // Example: xf|input:xxf-type('xs:date')[appearance ~= dropdowns]
    def datatypeAndAttributeBindingPF(
      ns      : NamespaceMapping,
      binding : Option[NodeInfo]
    ): PartialFunction[Selector, BindingDescriptor] = {
      case Selector(
          ElementWithFiltersSelector(
            typeSelectorOpt,
            List(
              FunctionalPseudoClassFilter("xxf-type", List(StringExpr(datatype))),
              AttributeFilter(None, attName, attPredicate)
            )
          ),
          Nil) =>

        BindingDescriptor(
          qNameFromElementSelector(typeSelectorOpt, ns),
          datatype.trimAllToOpt flatMap (Extensions.resolveQName(ns.mapping.get, _, unprefixedIsNoNamespace = true)) filterNot StringQNames,
          Some(BindingAttributeDescriptor(QName(attName), attPredicate))// TODO: QName for attName
        )(binding)
    }

    def getAllSelectorsWithPF(
      bindings  : Iterable[NodeInfo],
      collector : (NamespaceMapping, NodeInfo) => PartialFunction[Selector, BindingDescriptor]
    ): Iterable[BindingDescriptor] = {

      def getBindingSelectorsAndNamespaces(bindingElem: NodeInfo) =
        (bindingElem attValue "element", NamespaceMapping(bindingElem.namespaceMappings.toMap))

      def descriptorsForSelectors(selectors: String, ns: NamespaceMapping, binding: NodeInfo) =
        CSSSelectorParser.parseSelectors(selectors) collect collector(ns, binding)

      for {
        binding         <- bindings
        (selectors, ns) = getBindingSelectorsAndNamespaces(binding)
        descriptor      <- descriptorsForSelectors(selectors, ns, binding)
      } yield
        descriptor
    }

    def findStaticBindingInRelated(
      searchElemName     : QName,
      searchAppearances  : Set[String],
      relatedDescriptors : Iterable[BindingDescriptor]
    ): Option[(QName, Option[String])] = {

      def findByNameAndAppearance =
        relatedDescriptors collectFirst {
          case BindingDescriptor(
              Some(`searchElemName`),
              None,
              Some(BindingAttributeDescriptor(APPEARANCE_QNAME, AttributePredicate.Equal(appearance)))
            ) if searchAppearances(appearance) => searchElemName -> Some(appearance)
          case BindingDescriptor(
              Some(`searchElemName`),
              None,
              Some(BindingAttributeDescriptor(APPEARANCE_QNAME, AttributePredicate.Token(appearance)))
            ) if searchAppearances(appearance) => searchElemName -> Some(appearance)
        }

      def findByAppearanceOnly =
        relatedDescriptors collectFirst {
          case BindingDescriptor(
              Some(elemName),
              None,
              Some(BindingAttributeDescriptor(APPEARANCE_QNAME, AttributePredicate.Equal(appearance)))
            ) if searchAppearances(appearance) => elemName -> Some(appearance)
          case BindingDescriptor(
              Some(elemName),
              None,
              Some(BindingAttributeDescriptor(APPEARANCE_QNAME, AttributePredicate.Token(appearance)))
            ) if searchAppearances(appearance) => elemName -> Some(appearance)
        }

      def findDirect =
        relatedDescriptors collectFirst {
          case BindingDescriptor(
              Some(elemName),
              None,
              None
            ) => elemName -> None
        }

      findByNameAndAppearance orElse findByAppearanceOnly orElse findDirect
    }

    def findMostSpecificWithDatatype(
      searchElemName    : QName,
      searchDatatype    : QName,
      searchAppearances : Set[String],
      descriptors       : Iterable[BindingDescriptor]
    ): Option[BindingDescriptor] = {

      val Datatype1 = searchDatatype
      val Datatype2 = ModelDefs.getVariationTypeOrKeep(searchDatatype)

      def findWithDatatypeAndAppearance =
        descriptors collectFirst {
          case descriptor @ BindingDescriptor(
              Some(`searchElemName`),
              Some(Datatype1 | Datatype2),
              Some(BindingAttributeDescriptor(APPEARANCE_QNAME, AttributePredicate.Equal(appearance)))
            ) if searchAppearances(appearance) => descriptor
          case descriptor @ BindingDescriptor(
              Some(`searchElemName`),
              Some(Datatype1 | Datatype2),
              Some(BindingAttributeDescriptor(APPEARANCE_QNAME, AttributePredicate.Token(appearance)))
            ) if searchAppearances(appearance) => descriptor
        }

      def findWithDatatypeOnly =
        descriptors collectFirst {
          case descriptor @ BindingDescriptor(
              Some(`searchElemName`),
              Some(Datatype1 | Datatype2),
              None
            ) => descriptor
        }

      findWithDatatypeAndAppearance orElse findWithDatatypeOnly
    }

    def findRelatedVaryNameAndAppearance(
      searchDatatype     : QName,
      relatedDescriptors : Iterable[BindingDescriptor]
    ): Option[BindingDescriptor] = {

      val Datatype1 = searchDatatype
      val Datatype2 = ModelDefs.getVariationTypeOrKeep(searchDatatype)

      def findWithNameDatatypeAndAppearance =
        relatedDescriptors collectFirst {
          case descriptor @ BindingDescriptor(
              Some(_),
              Some(Datatype1 | Datatype2),
              Some(BindingAttributeDescriptor(APPEARANCE_QNAME, _))
            ) => descriptor
        }

      def findWithNameAndDatatype =
        relatedDescriptors collectFirst {
          case descriptor @ BindingDescriptor(
              Some(_),
              Some(Datatype1 | Datatype2),
              None
            ) => descriptor
        }

      def findWithNameAndAppearance =
        relatedDescriptors collectFirst {
          case descriptor @ BindingDescriptor(
              Some(_),
              None,
              Some(BindingAttributeDescriptor(APPEARANCE_QNAME, _))
            ) => descriptor
        }

      findWithNameDatatypeAndAppearance orElse
      findWithNameAndDatatype           orElse
      findWithNameAndAppearance
    }
  }
}
