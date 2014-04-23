/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import collection.JavaConverters._
import collection.immutable.Stream._
import collection.immutable.TreeMap
import collection.mutable.{HashSet, HashMap, LinkedHashMap, LinkedHashSet}
import java.util.{Map ⇒ JMap}
import org.dom4j.io.DocumentSource
import org.orbeon.oxf.resources.{ResourceManager, ResourceNotFoundException, ResourceManagerWrapper}
import org.orbeon.oxf.xforms.XFormsStaticStateImpl.StaticStateDocument
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xforms.xbl.XBLResources
import org.orbeon.oxf.xml.{TransformerUtils, NamespaceMapping, SAXStore}

/**
 * Container for element metadata gathered during document annotation/extraction:
 *
 * - id generation
 * - namespace mappings
 * - automatic XBL mappings
 * - full update marks
 *
 * Split into traits for modularity.
 */
class Metadata(val idGenerator: IdGenerator) extends NamespaceMappings with Bindings with Marks {
    def this() { this(new IdGenerator) }
}

object Metadata {
    // Restore a Metadata object from the given StaticStateDocument
    def apply(staticStateDocument: StaticStateDocument, template: Option[AnnotatedTemplate]): Metadata = {

        // Restore generator with last id
        val metadata = new Metadata(new IdGenerator(staticStateDocument.lastId))

        // Restore namespace mappings and ids
        TransformerUtils.sourceToSAX(new DocumentSource(staticStateDocument.xmlDocument), new XFormsAnnotator(metadata))

        // Restore marks if there is a template
        template foreach { template ⇒
            for (mark ← template.saxStore.getMarks.asScala)
                metadata.putMark(mark)
        }

        metadata
    }
}

// Handling of template marks
trait Marks {
    private val marks = new HashMap[String, SAXStore#Mark]

    def putMark(mark: SAXStore#Mark) = marks += mark.id → mark
    def getMark(prefixedId: String) = marks.get(prefixedId)

    private def topLevelMarks = marks collect { case (prefixedId, mark) if XFormsUtils.isTopLevelId(prefixedId) ⇒ mark }
    def hasTopLevelMarks = topLevelMarks.nonEmpty
}

// Handling of XBL bindings
trait Bindings {
    private val xblBindings = new HashMap[String, collection.mutable.Set[String]]
    private var lastModified = -1L

    val bindingIncludes = new LinkedHashSet[String]

    def isXBLBindingCheckAutomaticBindings(uri: String, localname: String): Boolean = {

        // Is this already registered?
        if (isXBLBinding(uri, localname))
            return true

        // If not, check if it exists as automatic binding
        XBLResources.getAutomaticXBLMappingPath(uri, localname) match {
            case Some(path) ⇒
                storeXBLBinding(uri, localname)
                bindingIncludes.add(path)
                true
            case _ ⇒
                false
        }
    }

    def storeXBLBinding(bindingURI: String, localname: String) {
        val localnames = xblBindings.getOrElseUpdate(bindingURI, new HashSet[String])
        localnames += localname
    }

    def isXBLBinding(uri: String, localname: String) =
        xblBindings.get(uri) match {
            case Some(localnames) ⇒ localnames(localname)
            case None ⇒ false
        }

    def getBindingIncludesJava = bindingIncludes.asJava

    def updateBindingsLastModified(lastModified: Long) {
        this.lastModified = math.max(this.lastModified, lastModified)
    }

    private def pathExistsAndIsUpToDate(path: String)(implicit rm: ResourceManager) = {
        val last = rm.lastModified(path, true)
        last != -1 && last <= this.lastModified
    }

    // Whether the binding includes are up to date
    def bindingsIncludesAreUpToDate = {
        implicit val rm = ResourceManagerWrapper.instance
        bindingIncludes.iterator forall pathExistsAndIsUpToDate
    }

    // For debugging only
    def debugOutOfDateBindingsIncludesJava = {
        implicit val rm = ResourceManagerWrapper.instance
        bindingIncludes.iterator filterNot pathExistsAndIsUpToDate mkString ", "
    }
}

// Handling of namespaces
trait NamespaceMappings {
    private val namespaceMappings = new HashMap[String, NamespaceMapping]
    private val hashes = new LinkedHashMap[String, NamespaceMapping]

    def addNamespaceMapping(prefixedId: String, mapping: JMap[String, String]): Unit = {
        // Sort mappings by prefix
        val sorted = TreeMap(mapping.asScala.toSeq: _*)
        // Hash key/values
        val hexHash = NamespaceMapping.hashMapping(sorted.asJava)

        // Retrieve or create mapping object
        val namespaceMapping = hashes.getOrElseUpdate(hexHash, {
            val newNamespaceMapping = new NamespaceMapping(hexHash, sorted.asJava)
            hashes += (hexHash → newNamespaceMapping)
            newNamespaceMapping
        })

        // Remember that id has this mapping
        namespaceMappings += prefixedId → namespaceMapping
    }

    def removeNamespaceMapping(prefixedId: String): Unit =
        namespaceMappings -= prefixedId

    def getNamespaceMapping(prefixedId: String) = namespaceMappings.get(prefixedId).orNull

    def debugPrintNamespaces() {
        println("Number of different namespace mappings: " + hashes.size)
        for ((key, value) ← hashes) {
            println("   hash: " + key)
            for ((prefix, uri) ← value.mapping.asScala)
                println("     " + prefix + " → " + uri)
        }
    }
}
