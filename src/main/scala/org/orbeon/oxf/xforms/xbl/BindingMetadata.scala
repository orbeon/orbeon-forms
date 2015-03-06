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

import org.dom4j.Element
import org.orbeon.css.CSSSelectorParser
import org.orbeon.css.CSSSelectorParser.{ElementWithFiltersSelector, Selector, TypeSelector}
import org.orbeon.oxf.resources.{ResourceManager, ResourceManagerWrapper}
import org.orbeon.oxf.util.Logging
import org.orbeon.oxf.xforms.XFormsUtils
import org.xml.sax.Attributes

import scala.collection.JavaConverters._

trait BindingMetadata extends Logging {

    // NOTE: Multiple prefixed ids can point to the same AbstractBinding object.
    private var bindingsByControlPrefixedId = Map[String, IndexableBinding]()
    private var bindingsPaths               = Set[String]()
    private var maxLastModified             = -1L

    // ==== Annotator/Extractor API
    
    // Preemptively ensure the XBL library is up to date
    private var (xblIndex: BindingIndex[IndexableBinding], checkedPaths, _, _) =
        BindingLoader.getUpToDateLibraryAndBaseline(GlobalBindingIndex.currentIndex, checkUpToDate = true)

    def commitBindingIndex() = {
        val cleanIndex = BindingIndex.keepBindingsWithPathOnly(xblIndex)
        debug("committing global binding index", BindingIndex.stats(cleanIndex))
        GlobalBindingIndex.updateIndex(cleanIndex)
    }

    def registerInlineBinding(ns: Map[String, String], elementAtt: String, bindingPrefixedId: String): Unit = {

        debug("registering inline binding", List("element" → elementAtt, "prefixed id" → bindingPrefixedId))

        val binding =
            InlineBindingRef(
                bindingPrefixedId,
                CSSSelectorParser.parseSelectors(elementAtt),
                ns
            )

        xblIndex = BindingIndex.indexBinding(xblIndex, binding)
    }

    def findBindingForElement(uri: String, localname: String, atts: Attributes): Option[IndexableBinding] = {

        val (newIndex, newPaths, bindingOpt) =
            BindingLoader.findMostSpecificBinding(xblIndex, Some(checkedPaths), uri, localname, atts)

        xblIndex     = newIndex
        checkedPaths = newPaths

        if (debugEnabled)
            bindingOpt foreach { binding ⇒
                debug("found binding for", List("element" → s"Q{$uri}$localname"))
            }

        bindingOpt
    }

    def mapBindingToElement(controlPrefixedId: String, binding: IndexableBinding) = {
        bindingsByControlPrefixedId += controlPrefixedId → binding
        binding.path foreach (bindingsPaths += _)
        maxLastModified = maxLastModified max binding.lastModified
    }

    def prefixedIdHasBinding(prefixedId: String): Boolean =
        bindingsByControlPrefixedId.contains(prefixedId)
    
    def isByNameBindingInUse(uri: String, localname: String): Boolean = {

        val someURI = Some(uri)

        def hasByNameSelector(binding: IndexableBinding) = {

            val ns = binding.selectorsNamespaces

            binding.selectors collectFirst {
                case s @ Selector(
                    ElementWithFiltersSelector(
                        Some(TypeSelector(Some(Some(prefix)), `localname`)),
                        Nil),
                    Nil) if ns.get(prefix) == someURI ⇒
            } isDefined
        }

        bindingsByControlPrefixedId.values exists hasByNameSelector
    }

    // ==== XBLBindings API
    
    def extractInlineXBL(inlineXBL: Seq[Element], scope: Scope): Unit = {

        val (newIndex, newBindings) = BindingLoader.extractAndIndexFromElements(xblIndex, inlineXBL)

        debug("extracted inline XBL", List("elements" → inlineXBL.size.toString, "bindings" → newBindings.size.toString))

        def replaceBindingRefs(mappings: Map[String, IndexableBinding], newBindings: List[AbstractBinding]) = {

            var currentMappings = mappings

            newBindings foreach { newBinding ⇒

                val bindingPrefixedId = scope.fullPrefix + XFormsUtils.getElementId(newBinding.bindingElement)

                currentMappings foreach {
                    case (controlPrefixedId, ib @ InlineBindingRef(`bindingPrefixedId`, _, _)) ⇒
                        currentMappings += controlPrefixedId → newBinding
                    case _ ⇒
                }
            }

            currentMappings
        }

        // Replace in bindingsByControlPrefixedId
        bindingsByControlPrefixedId = replaceBindingRefs(bindingsByControlPrefixedId, newBindings)

        // Remove from xblIndex (AbstractBinding are added by extractAndIndexFromElements)
        xblIndex = BindingIndex.keepAbstractBindingsOnly(newIndex)
    }

    def findBindingByPrefixedId(controlPrefixedId: String): Option[AbstractBinding] =
        bindingsByControlPrefixedId.get(controlPrefixedId) collect {
            case binding: AbstractBinding ⇒ binding
            case _                        ⇒ throw new IllegalStateException("missing binding")
        }
    
    // ==== Other API

    def allBindingsMaybeDuplicates = bindingsByControlPrefixedId.values collect { case b: AbstractBinding ⇒ b }

    def getBindingIncludesJava = bindingsPaths.asJava

    private def pathExistsAndIsUpToDate(path: String)(implicit rm: ResourceManager) = {
        val last = rm.lastModified(path, true)
        last != -1 && last <= this.maxLastModified
    }

    def bindingsIncludesAreUpToDate = {
        implicit val rm = ResourceManagerWrapper.instance
        bindingsPaths.iterator forall pathExistsAndIsUpToDate
    }

    def debugOutOfDateBindingsIncludesJava = {
        implicit val rm = ResourceManagerWrapper.instance
        bindingsPaths.iterator filterNot pathExistsAndIsUpToDate mkString ", "
    }
}