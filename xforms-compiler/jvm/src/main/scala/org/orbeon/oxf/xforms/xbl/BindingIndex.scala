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
import org.orbeon.oxf.util.StringUtils._

import scala.collection.mutable

case class BindingIndex[+T](
  nameAndAttSelectors : List[(BindingDescriptor, T)],
  attOnlySelectors    : List[(BindingDescriptor, T)],
  nameOnlySelectors   : List[(BindingDescriptor, T)]
) {
  // Index by name to make lookups faster
  val byNameWithAtt = nameAndAttSelectors filter (_._1.elementName.isDefined) groupBy (_._1.elementName.get)
  val byNameOnly    = nameOnlySelectors   filter (_._1.elementName.isDefined) groupBy (_._1.elementName.get)
}

// Implementation strategy: all the functions take an immutable index and return a new immutable index. The global index
// can be retrieved and updated atomically. There is no lock on the index. This assumes that the index is only a cache
// and that there are not too many concurrent operations on the index.
object BindingIndex {

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
    index   : BindingIndex[IndexableBinding],
    binding : IndexableBinding
  ): BindingIndex[IndexableBinding] = {

    val ns = binding.namespaceMapping

    val attDescriptors      = binding.selectors.iterator collect BindingDescriptor.attributeBindingPF(ns, None)
    val nameOnlyDescriptors = binding.selectors.iterator collect BindingDescriptor.directBindingPF(ns, None)

    var currentIndex = index

    attDescriptors ++ nameOnlyDescriptors foreach {
      case b @ BindingDescriptor(Some(elemName), None, Some(BindingAttributeDescriptor(name, predicate))) =>
        currentIndex = currentIndex.copy(nameAndAttSelectors = (b -> binding) :: currentIndex.nameAndAttSelectors)
      case b @ BindingDescriptor(None, None, Some(BindingAttributeDescriptor(name, predicate))) =>
        currentIndex = currentIndex.copy(attOnlySelectors = (b -> binding) :: currentIndex.attOnlySelectors)
      case b @ BindingDescriptor(Some(elemName), None, None) =>
        currentIndex = currentIndex.copy(nameOnlySelectors = (b -> binding) :: currentIndex.nameOnlySelectors)
      case _ =>
    }

    currentIndex
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
  def findMostSpecificBinding(
    index : BindingIndex[IndexableBinding],
    qName : QName,
    atts  : Iterable[(QName, String)]
  ): Option[(IndexableBinding, Boolean)] = {

    def attValueMatches(attPredicate: AttributePredicate, attValue: String) = attPredicate match {
      case AttributePredicate.Exist           => true
      case AttributePredicate.Equal   (value) => attValue == value
      case AttributePredicate.Token   (value) => attValue.tokenizeToSet.contains(value)
      case AttributePredicate.Lang    (value) => attValue == value || attValue.startsWith(value + '-')
      case AttributePredicate.Start   (value) => value != "" && attValue.startsWith(value)
      case AttributePredicate.End     (value) => value != "" && attValue.endsWith(value)
      case AttributePredicate.Contains(value) => value != "" && attValue.contains(value)
    }

    def attMatches(attDesc: BindingAttributeDescriptor) =
      atts exists {
        case (attName, attValue) => attName == attDesc.name && attValueMatches(attDesc.predicate, attValue)
      }

    def attExists(name: QName) =
      atts exists {
        case (attName, _) => attName == name
      }

    def fromNameAndAttValue =
      index.byNameWithAtt.get(qName) flatMap { indexedBindings =>
        indexedBindings.collectFirst {
          case (BindingDescriptor(_, None, Some(attDesc)), binding) if attMatches(attDesc) =>
            (binding, false)
        }
      }

    def fromNameAndAttExistence =
      index.byNameWithAtt.get(qName) flatMap { indexedBindings =>
        indexedBindings.collectFirst {
          case (BindingDescriptor(_, None, Some(BindingAttributeDescriptor(attName, AttributePredicate.Exist))), binding) if attExists(attName) =>
            (binding, false)
        }
      }

    def fromAttValueOnly =
      index.attOnlySelectors collectFirst {
        case (BindingDescriptor(None, None, Some(attDesc)), binding) if attMatches(attDesc) =>
          (binding, false)
      }

    def fromAttExistenceOnly =
      index.attOnlySelectors collectFirst {
        case (BindingDescriptor(None, None, Some(BindingAttributeDescriptor(attName, AttributePredicate.Exist))), binding) if attExists(attName) =>
          (binding, false)
      }

    def fromNameOnly =
      index.byNameOnly.get(qName) flatMap (_.headOption) map (_._2 -> true)

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
    fromNameAndAttValue       orElse
      fromNameAndAttExistence orElse
      fromAttValueOnly        orElse
      fromAttExistenceOnly    orElse
      fromNameOnly
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
