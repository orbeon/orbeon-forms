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

import collection.immutable.TreeMap
import collection.JavaConversions._
import org.orbeon.oxf.xml.{NamespaceMapping, SAXStore}
import org.orbeon.oxf.xforms.XFormsUtils
import collection.mutable.{HashSet, HashMap, LinkedHashMap, LinkedHashSet}
import collection.immutable.Stream._
import java.util.{Map => JMap, Set => JSet}

import org.orbeon.oxf.xforms.xbl.XBLBindingsBase
import org.orbeon.oxf.resources.{ResourceNotFoundException, ResourceManagerWrapper}
import org.orbeon.oxf.properties.Properties

/**
 * Container for element metadata gathered during document annotation/extraction:
 *
 * - id generation
 * - namespace mappings
 * - automatic XBL mappings
 * - full update marks
 */
class Metadata(val idGenerator: IdGenerator) {

    def this() { this(new IdGenerator) }

    val marks = new HashMap[String, SAXStore#Mark]
    val bindingIncludes = new LinkedHashSet[String]

    private val namespaceMappings = new HashMap[String, NamespaceMapping]
    private val hashes = new LinkedHashMap[String, NamespaceMapping]
    private val xblBindings = new HashMap[String, collection.mutable.Set[String]]

    private var lastModified = -1L

    private lazy val automaticMappings = {
        val propertySet = Properties.instance.getPropertySet
        for {
            propertyName <- propertySet.getPropertiesStartsWith(XBLBindingsBase.XBL_MAPPING_PROPERTY_PREFIX)
            prefix = propertyName.substring(XBLBindingsBase.XBL_MAPPING_PROPERTY_PREFIX.length)
        } yield
            (propertySet.getString(propertyName), prefix)
    } toMap

    def addNamespaceMapping(prefixedId: String, mapping: JMap[String, String]) {
        // Sort mappings by prefix
        val sorted = TreeMap(mapping.toSeq: _*)
        // Hash key/values
        val hexHash = NamespaceMapping.hashMapping(sorted)

        // Retrieve or create mapping object
        val namespaceMapping = hashes.getOrElseUpdate(hexHash, {
            val newNamespaceMapping = new NamespaceMapping(hexHash, sorted)
            hashes += (hexHash -> newNamespaceMapping)
            newNamespaceMapping
        })

        // Remember that id has this mapping
        namespaceMappings += (prefixedId -> namespaceMapping)
    }

    def getNamespaceMapping(prefixedId: String) = namespaceMappings.get(prefixedId).orNull

    // NOTE: Top-level id if static id == prefixed id
    def hasTopLevelMarks = marks.keySet exists (prefixedId => prefixedId == XFormsUtils.getStaticIdFromId(prefixedId))

    def getElementMark(prefixedId: String) = marks.get(prefixedId).orNull

    // E.g. fr:tabview -> oxf:/xbl/orbeon/tabview/tabview.xbl
    def getAutomaticXBLMappingPath(uri: String, localname: String) =
        automaticMappings.get(uri) match {
            case Some(prefix) =>
                val path = "/xbl/" + prefix + '/' + localname + '/' + localname + ".xbl"
                if (ResourceManagerWrapper.instance.exists(path)) path else null
            case None => null
        }

    def isXBLBindingCheckAutomaticBindings(uri: String, localname: String): Boolean = {

        // Is this already registered?
        if (isXBLBinding(uri, localname))
            return true

        // If not, check if it exists as automatic binding
        getAutomaticXBLMappingPath(uri, localname) match {
            case path: String =>
                storeXBLBinding(uri, localname)
                bindingIncludes.add(path)
                true
            case _ => false
        }
    }

    def storeXBLBinding(bindingURI: String, localname: String) {
        val localnames = xblBindings.getOrElseUpdate(bindingURI, new HashSet[String])
        localnames += localname
    }

    def isXBLBinding(uri: String, localname: String) =
        xblBindings.get(uri) match {
            case Some(localnames) => localnames(localname)
            case None => false
        }

    def getBingingIncludes: JSet[String] = bindingIncludes

    def updateBindingsLastModified(lastModified: Long) {
        this.lastModified = math.max(this.lastModified, lastModified)
    }

    /**
     * Check if the binding includes are up to date.
     */
    def checkBindingsIncludes =
        try {
            // true if all last modification dates are at most equal to our last modification date
            bindingIncludes.toStream map
                (ResourceManagerWrapper.instance.lastModified(_, false)) forall
                    (_ <= this.lastModified)
        } catch {
            // If a resource cannot be found, consider that something has changed
            case e: ResourceNotFoundException => false
        }

    def debugReadOut() {
        System.out.println("Number of different namespace mappings: " + hashes.size)
        for (entry <- hashes.entrySet) {
            System.out.println("   hash: " + entry.getKey)
            for (mapping <- entry.getValue.mapping.entrySet)
                System.out.println("     hash: " + mapping.getKey + " -> " + mapping.getValue)
        }
    }
}