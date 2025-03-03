/**
 * Copyright (C) 2015 Orbeon, Inc.
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

import org.orbeon.css.CSSSelectorParser.AttributePredicate
import org.orbeon.dom.QName
import org.orbeon.oxf.xforms.analysis.model.Types
import org.orbeon.oxf.xforms.analysis.model.Types.StringQNames
import org.orbeon.oxf.xforms.xbl.BindingDescriptor.{AppearanceExtractor, FirstAttExtractor}

import scala.collection.mutable


case class BindingIndex[+T](
  nameAndAttSelectors : List[(BindingDescriptor, T)],
  attOnlySelectors    : List[(BindingDescriptor, T)],
  nameOnlySelectors   : List[(BindingDescriptor, T)]
) {
  // Index by name to make lookups faster
  val byNameWithAtt: Map[QName, List[(BindingDescriptor, T)]] = filterAndGroup(nameAndAttSelectors)
  val byNameOnly   : Map[QName, List[(BindingDescriptor, T)]] = filterAndGroup(nameOnlySelectors)

  def iterateRelatedDescriptors(bindingDescriptor: BindingDescriptor): Iterator[BindingDescriptor] =
    iterateDescriptors.filter(_.binding == bindingDescriptor.binding)

  def iterateDescriptors: Iterator[BindingDescriptor] =
    (nameAndAttSelectors.iterator ++ attOnlySelectors ++ nameOnlySelectors).map(_._1)

  private def filterAndGroup[U](selectors: List[(BindingDescriptor, U)]): Map[QName, List[(BindingDescriptor, U)]] =
    selectors filter (_._1.elementName.isDefined) groupBy (_._1.elementName.get)
}

// Implementation strategy: all the functions take an immutable index and return a new immutable index. The global index
// can be retrieved and updated atomically. There is no lock on the index. This assumes that the index is only a cache
// and that there are not too many concurrent operations on the index.
object BindingIndex {

  sealed trait DatatypeMatch

  object DatatypeMatch {
    case object Exclude             extends DatatypeMatch
    case object Ignore              extends DatatypeMatch
    case class  Match(qName: QName) extends DatatypeMatch

    def makeExcludeStringMatch(qName: QName): DatatypeMatch =
      if (StringQNames(qName))
        Exclude
      else
        Match(qName)
  }

  def stats(index: BindingIndex[IndexableBinding]) = List(
    "name and attribute selectors" -> index.nameAndAttSelectors.size.toString,
    "attribute only selectors"     -> index.attOnlySelectors.size.toString,
    "name only selectors"          -> index.nameOnlySelectors.size.toString,
    "distinct bindings"            -> distinctBindings(index).size.toString
  )

  def distinctBindingsForPath(index: BindingIndex[IndexableBinding], path: String): List[IndexableBinding] = {
    val somePath = Some(path)
    distinctBindings(index) collect { case binding if binding.path == somePath => binding }
  }

  def distinctBindings(index: BindingIndex[IndexableBinding]): List[IndexableBinding] = {

    val builder = mutable.ListBuffer[IndexableBinding]()

    val allIterator =
      index.nameAndAttSelectors.iterator ++
      index.attOnlySelectors.iterator    ++
      index.nameOnlySelectors.iterator

    allIterator foreach {
      case (_, binding) =>
        if (! (builder exists (_ eq binding)))
          builder += binding
    }

    builder.result()
  }

  def indexBinding(
    index                      : BindingIndex[IndexableBinding],
    binding                    : IndexableBinding
  ): BindingIndex[IndexableBinding] = {

    val ns = binding.namespaceMapping

    val attDescriptors      = binding.selectors.iterator collect BindingDescriptor.attributeBindingPF(ns, None, includeBindingsWithDatatype = false)
    val nameOnlyDescriptors = binding.selectors.iterator collect BindingDescriptor.directBindingPF(ns, None)

    (attDescriptors ++ nameOnlyDescriptors).foldLeft(index) { case (index, bindingDescriptor) =>
      indexBindingDescriptor(index, binding, bindingDescriptor, includeBindingsWithDatatype = false)
    }
  }

  def indexBindingDescriptor[T](
    index                      : BindingIndex[T],
    value                      : T,
    bindingDescriptor          : BindingDescriptor,
    includeBindingsWithDatatype: Boolean
  ): BindingIndex[T] =
    bindingDescriptor match {
      case b @ BindingDescriptor(Some(_), datatypeOpt, None, None)
        if includeBindingsWithDatatype || datatypeOpt.isEmpty =>
          index.copy(nameOnlySelectors   = (b -> value) :: index.nameOnlySelectors)
      case b @ BindingDescriptor(Some(_), datatypeOpt, _, _)
        if includeBindingsWithDatatype || datatypeOpt.isEmpty =>
          index.copy(nameAndAttSelectors = (b -> value) :: index.nameAndAttSelectors)
      case b @ BindingDescriptor(None, datatypeOpt, _, _)
        if includeBindingsWithDatatype || datatypeOpt.isEmpty =>
          index.copy(attOnlySelectors    = (b -> value) :: index.attOnlySelectors)
      case _ =>
        index
    }

  def deIndexBinding(
    index   : BindingIndex[IndexableBinding],
    binding : IndexableBinding
  ): BindingIndex[IndexableBinding] =
    index.copy(
      nameAndAttSelectors = index.nameAndAttSelectors filterNot (_._2 eq binding),
      attOnlySelectors    = index.attOnlySelectors    filterNot (_._2 eq binding),
      nameOnlySelectors   = index.nameOnlySelectors   filterNot (_._2 eq binding)
    )

  def deIndexBindingByPath(index: BindingIndex[IndexableBinding], path: String): BindingIndex[IndexableBinding] = {

    val somePath = Some(path)

    index.copy(
      nameAndAttSelectors = index.nameAndAttSelectors filterNot (_._2.path == somePath),
      attOnlySelectors    = index.attOnlySelectors    filterNot (_._2.path == somePath),
      nameOnlySelectors   = index.nameOnlySelectors   filterNot (_._2.path == somePath)
    )
  }

  // If found return the binding and a Boolean flag indicating if the match was by name only.
  def findMostSpecificBinding[T](
    index           : BindingIndex[T],
    qName           : QName,
    datatypeMatch   : DatatypeMatch,
    atts            : Iterable[(QName, String)],
    filterAppearance: AttributePredicate => Boolean = _ => true
  ): Option[(T, Boolean)] = {

    object AppearanceExtractor extends AppearanceExtractor {
      def getAtts: Iterable[(QName, String)] = atts
      def getFilterAppearance: AttributePredicate => Boolean = filterAppearance
    }

    object TypeExtractor {

      val (datatype1Opt, datatype2Opt) =
        datatypeMatch match {
          case DatatypeMatch.Match(datatype) =>
            (Some(datatype), Some(Types.getVariationTypeOrKeep(datatype)))
          case _ =>
            (None, None)
        }

      def unapply(datatypeOpt: Option[QName]): Boolean =
        datatypeMatch match {
          case DatatypeMatch.Exclude  => datatypeOpt.isEmpty
          case DatatypeMatch.Ignore   => true
          case DatatypeMatch.Match(_) => datatypeOpt == datatype1Opt || datatypeOpt == datatype2Opt
        }
    }

    object FirstAttExtractor extends FirstAttExtractor {
      def getAtts: Iterable[(QName, String)] = atts
    }

    def fromNameAndAppearanceAndAtt: Option[(T, Boolean)] =
      index.byNameWithAtt.get(qName) flatMap { indexedBindings =>
        indexedBindings.collectFirst {
          case (BindingDescriptor(_, TypeExtractor(), AppearanceExtractor(_), FirstAttExtractor(_)), binding) => (binding, false)
        }
      }

    def fromNameAndAppearanceOrAtt: Option[(T, Boolean)] =
      index.byNameWithAtt.get(qName) flatMap { indexedBindings =>
        indexedBindings.collectFirst {
          case (BindingDescriptor(_, TypeExtractor(), AppearanceExtractor(_), None), binding) => (binding, false)
          case (BindingDescriptor(_, TypeExtractor(), None, FirstAttExtractor(_)), binding)  => (binding, false)
        }
      }

    def fromAppearanceAndAtt: Option[(T, Boolean)] =
      index.attOnlySelectors collectFirst {
        case (BindingDescriptor(None, TypeExtractor(), AppearanceExtractor(_), FirstAttExtractor(_)), binding) => (binding, false)
      }

    def fromAppearanceOrAtt: Option[(T, Boolean)] =
      index.attOnlySelectors collectFirst {
        case (BindingDescriptor(None, TypeExtractor(), AppearanceExtractor(_), None), binding) => (binding, false)
        case (BindingDescriptor(None, TypeExtractor(), None, FirstAttExtractor(_)), binding)  => (binding, false)
      }

    def fromName: Option[(T, Boolean)] =
      index.byNameOnly.get(qName).flatMap {
        _.collectFirst {
          case (BindingDescriptor(_, TypeExtractor(), None, None), binding) => (binding, true)
        }
      }

    // Specificity: https://drafts.csswg.org/selectors-4/#specificity
    //
    //   foo[bar ~= baz] > [bar ~= baz] > foo
    //
    // It doesn't look like the spec specifies that:
    //
    //   [bar ~= baz] > [bar]
    //
    // But that would be reasonable.
    //
    fromNameAndAppearanceAndAtt  orElse
      fromNameAndAppearanceOrAtt orElse
      fromAppearanceAndAtt       orElse
      fromAppearanceOrAtt        orElse
      fromName
  }

  private val abstractPF: PartialFunction[(BindingDescriptor, IndexableBinding), (BindingDescriptor, AbstractBinding)] =
    { case (d, b: AbstractBinding) =>  d -> b }

  private val pathsPF: PartialFunction[(BindingDescriptor, IndexableBinding), (BindingDescriptor, AbstractBinding)] =
    { case (d, b: AbstractBinding) if b.path.isDefined =>  d -> b }

  def keepAbstractBindingsOnly(index: BindingIndex[IndexableBinding]): BindingIndex[AbstractBinding] =
    index.copy(
      nameAndAttSelectors = index.nameAndAttSelectors collect abstractPF,
      attOnlySelectors    = index.attOnlySelectors    collect abstractPF,
      nameOnlySelectors   = index.nameOnlySelectors   collect abstractPF
    )

  def keepBindingsWithPathOnly(index: BindingIndex[IndexableBinding]): BindingIndex[AbstractBinding] =
    index.copy(
      nameAndAttSelectors = index.nameAndAttSelectors collect pathsPF,
      attOnlySelectors    = index.attOnlySelectors    collect pathsPF,
      nameOnlySelectors   = index.nameOnlySelectors   collect pathsPF
    )
}

object GlobalBindingIndex {

  val Empty = BindingIndex[AbstractBinding](Nil, Nil, Nil)

  // The immutable, shared index, which is updated atomically each time a form has completed compilation
  @volatile private var _index: Option[BindingIndex[AbstractBinding]] = None

  def currentIndex: Option[BindingIndex[AbstractBinding]] = _index
  def updateIndex(index: BindingIndex[AbstractBinding]): Unit = _index = Some(index)
}
