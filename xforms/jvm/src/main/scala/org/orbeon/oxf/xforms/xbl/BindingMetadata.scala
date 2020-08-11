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

import java.util

import org.orbeon.css.CSSSelectorParser
import org.orbeon.css.CSSSelectorParser.{ElementWithFiltersSelector, Selector, TypeSelector}
import org.orbeon.dom.Element
import org.orbeon.oxf.resources.{ResourceManager, ResourceManagerWrapper}
import org.orbeon.oxf.util.Logging
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xml.NamespaceMapping
import org.xml.sax.Attributes

import scala.collection.JavaConverters._

trait BindingMetadata extends Logging {

  private var inlineBindingsRefs          = List[InlineBindingRef]()
  // NOTE: Multiple prefixed ids can point to the same AbstractBinding object.
  private var bindingsByControlPrefixedId = Map[String, IndexableBinding]()
  private var bindingsPaths               = Set[String]()
  private var maxLastModified             = -1L

  // ==== Annotator/Extractor API

  private var _xblIndex          : Option[BindingIndex[IndexableBinding]] = None
  private var _checkedPaths      : Set[String]= Set.empty
  private var _baselineResources : (List[String], List[String]) = (Nil, Nil)

  def initializeBindingLibraryIfNeeded(): Unit =
    if (_xblIndex.isEmpty) {

      debug("entering view")

      val (newIndex, newCheckedPaths, scripts, styles) =
        BindingLoader.getUpToDateLibraryAndBaseline(GlobalBindingIndex.currentIndex, checkUpToDate = true)

      var currentIndex = newIndex

      if (inlineBindingsRefs.nonEmpty) {
        debug(s"indexing ${inlineBindingsRefs.size} inline bindings")

        inlineBindingsRefs foreach { inlineBinding =>
          currentIndex = BindingIndex.indexBinding(currentIndex, inlineBinding)
        }
        inlineBindingsRefs = Nil
      }

      _xblIndex          = Some(currentIndex)
      _checkedPaths      = newCheckedPaths
      _baselineResources = (scripts, styles)
    }

  def commitBindingIndex(): Unit =
    _xblIndex match {
      case Some(index) =>
        val cleanIndex = BindingIndex.keepBindingsWithPathOnly(index)
        debug("committing global binding index", BindingIndex.stats(cleanIndex))
        GlobalBindingIndex.updateIndex(cleanIndex)
      case None =>
        debug("no binding index to commit")
    }

  def registerInlineBinding(ns: NamespaceMapping, elementAtt: String, bindingPrefixedId: String): Unit = {
    debug(
      "registering inline binding",
      List(
        "index"       -> _xblIndex.isDefined.toString,
        "element"     -> elementAtt,
        "prefixed id" -> bindingPrefixedId
      )
    )
    val newBindingRef = InlineBindingRef(
      bindingPrefixedId,
      CSSSelectorParser.parseSelectors(elementAtt),
      ns
    )
    _xblIndex match {
      case Some(index) =>
        // Case of restore, where `initializeBindingLibraryIfNeeded()` is called first
        val newIndex = BindingIndex.indexBinding(index, newBindingRef)
        if (index ne newIndex)
          _xblIndex = Some(newIndex)
      case None =>
        // Case of top-level initial analysis, where the library gets initialized once the body is found
        // `inlineBindingsRefs` is processed during subsequent `initializeBindingLibraryIfNeeded()`
        debug("registering inline binding", List("element" -> elementAtt, "prefixed id" -> bindingPrefixedId))
        inlineBindingsRefs ::= newBindingRef
    }
  }

  def findBindingForElement(uri: String, localname: String, atts: Attributes): Option[IndexableBinding] =
    _xblIndex flatMap { index =>

    val (newIndex, newPaths, bindingOpt) =
      BindingLoader.findMostSpecificBinding(index, Some(_checkedPaths), uri, localname, atts)

    if (index ne newIndex)
      _xblIndex = Some(newIndex)

    _checkedPaths = newPaths

    if (debugEnabled)
      bindingOpt foreach { _ =>
        debug("found binding for", List("element" -> s"Q{$uri}$localname"))
      }

    bindingOpt
  }

  def mapBindingToElement(controlPrefixedId: String, binding: IndexableBinding): Unit = {
    bindingsByControlPrefixedId += controlPrefixedId -> binding
    binding.path foreach (bindingsPaths += _)
    maxLastModified = maxLastModified max binding.lastModified
  }

  def prefixedIdHasBinding(prefixedId: String): Boolean =
    bindingsByControlPrefixedId.contains(prefixedId)

  // NOTE: Used for hooking up fr:xforms-inspector.
  def isByNameBindingInUse(uri: String, localname: String): Boolean = {

    val someURI = Some(uri)

    def hasByNameSelector(binding: IndexableBinding) = {

      val ns = binding.namespaceMapping

      binding.selectors collectFirst {
        case Selector(
          ElementWithFiltersSelector(
            Some(TypeSelector(Some(Some(prefix)), `localname`)),
            Nil),
          Nil) if ns.mapping.get(prefix) == someURI =>
      } isDefined
    }

    bindingsByControlPrefixedId.values exists hasByNameSelector
  }

  // ==== XBLBindings API

  def extractInlineXBL(inlineXBL: Seq[Element], scope: Scope): Unit =
    _xblIndex foreach { index =>

      val (newIndex, newBindings) = BindingLoader.extractAndIndexFromElements(index, inlineXBL)

      debug("extracted inline XBL", List(
        "elements" -> inlineXBL.size.toString,
        "bindings" -> newBindings.size.toString
      ))

      def replaceBindingRefs(mappings: Map[String, IndexableBinding], newBindings: List[AbstractBinding]) = {

        var currentMappings = mappings

        newBindings foreach { newBinding =>

          val bindingPrefixedId = scope.fullPrefix + XFormsUtils.getElementId(newBinding.bindingElement)

          currentMappings foreach {
            case (controlPrefixedId, InlineBindingRef(`bindingPrefixedId`, _, _)) =>
              currentMappings += controlPrefixedId -> newBinding
            case _ =>
          }
        }

        currentMappings
      }

      // Replace in bindingsByControlPrefixedId
      bindingsByControlPrefixedId = replaceBindingRefs(bindingsByControlPrefixedId, newBindings)

      // Remove InlineBindingRef if any (AbstractBinding are added by extractAndIndexFromElements)
      if (newIndex ne index)
        _xblIndex = Some(BindingIndex.keepAbstractBindingsOnly(newIndex))
    }

  def findAbstractBindingByPrefixedId(controlPrefixedId: String): Option[AbstractBinding] =
    bindingsByControlPrefixedId.get(controlPrefixedId) collect {
      case binding: AbstractBinding => binding
      case _                        => throw new IllegalStateException("missing binding")
    }

  def removeBindingByPrefixedId(controlPrefixedId: String): Unit =
    bindingsByControlPrefixedId -= controlPrefixedId

  // ==== Other API

  def baselineResources          : (List[String], List[String]) = _baselineResources
  def allBindingsMaybeDuplicates : Iterable[AbstractBinding]    = bindingsByControlPrefixedId.values collect { case b: AbstractBinding => b }
  def bindingIncludes            : Set[String]                  = bindingsPaths

  private def pathExistsAndIsUpToDate(path: String)(implicit rm: ResourceManager) = {
    val last = rm.lastModified(path, true)
    last != -1 && last <= this.maxLastModified
  }

  def bindingsIncludesAreUpToDate: Boolean = {
    implicit val rm = ResourceManagerWrapper.instance
    bindingsPaths.iterator forall pathExistsAndIsUpToDate
  }

  def debugOutOfDateBindingsIncludesJava: String = {
    implicit val rm = ResourceManagerWrapper.instance
    bindingsPaths.iterator filterNot pathExistsAndIsUpToDate mkString ", "
  }
}