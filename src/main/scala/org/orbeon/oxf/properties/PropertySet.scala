/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.properties

import org.apache.commons.lang3.StringUtils
import org.dom4j.Element
import org.dom4j.QName
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import java.net.URI
import java.util.{List ⇒ JList, Map ⇒ JMap, Set ⇒ JSet, Date ⇒ JDate}
import java.lang.{Boolean ⇒ JBoolean, Integer ⇒ JInteger}
import collection.JavaConverters._
import collection.mutable

/**
 * Represent a set of properties.
 *
 * A property name can be exact, e.g. foo.bar.gaga, or it can contain wildcards, like ".*.bar.gaga", "foo.*.gaga", or
 * "foo.bar.*", or "*.bar.*", etc.
 *
 * TODO: Make this effectively immutable and remove `setProperty`.
 */
class PropertySet {
    
    import JPropertySet._

    private var exactProperties = Map[String, Property]()
    private val wildcardProperties = new PropertyNode

    /**
     * Set a property. Used by PropertyStore.
     *
     * @param element         Element on which the property is defined. Used for QName resolution if needed.
     * @param name            property name
     * @param typ             property type, or null
     * @param stringValue     property string value
     */
    def setProperty(element: Element, name: String, typ: QName, stringValue: String) {
        val value = PropertyStore.getObjectFromStringValue(stringValue, typ, element)
        val property = Property(typ, value, Dom4jUtils.getNamespaceContext(element).asScala.toMap)
        
        // Store exact property name anyway
        exactProperties += name → property
        
        // Also store in tree (in all cases, not only when contains wildcard, so we get find all the properties that start with some token)
        var currentNode = wildcardProperties
        for (currentToken ← StringUtils.split(name, ".")) {
            if (currentNode.children eq null)
                currentNode.children = mutable.LinkedHashMap[String, PropertyNode]()
            
            currentNode = currentNode.children.getOrElseUpdate(currentToken, new PropertyNode)
        }
        
        // Store value
        currentNode.property = property
    }

    def keySet: JSet[String] = exactProperties.keySet.asJava
    def size = exactProperties.size

    /**
     * an unmodifiable Map<String, Boolean> of all Boolean properties.
     */
    def getBooleanProperties: JMap[String, JBoolean] = {
        val tuples = 
            for {
                key ← exactProperties.keys
                o = getObject(key)
                if o.isInstanceOf[JBoolean]
            } yield
                key → o.asInstanceOf[JBoolean]
        
        tuples.toMap.asJava
    }

    // Return all the properties starting with the given name
    def propertiesStartsWith(name: String): Seq[String] = {
        
        val result = mutable.Buffer[String]()
        
        def processNode(propertyNode: PropertyNode, consumed: String, tokens: Array[String], currentTokenPosition: Int): Unit = {
            
            def appendToConsumed(s: String) = if (consumed.length == 0) s else consumed + "." + s
            
            val token = if (currentTokenPosition >= tokens.length) null else tokens(currentTokenPosition)
            if (Set("*", null)(token)) {
                if (propertyNode.children == null && token == null)
                    result += consumed
                
                // Go through all children
                if (propertyNode.children ne null) {
                    for ((key, value) ← propertyNode.children) {
                        val newConsumed = appendToConsumed(key)
                        processNode(value, newConsumed, tokens, currentTokenPosition + 1)
                    }
                }
            } else {
                // Regular token
                
                // Find 1. property node with exact name 2. property node with *
                val newPropertyNodes = Seq(propertyNode.children.get(token), propertyNode.children.get("*"))
                for {
                    (newPropertyNodeOpt, index) ← newPropertyNodes.zipWithIndex
                    newPropertyNode ← newPropertyNodeOpt
                    actualToken = if (index == 0) token else "*"
                    newConsumed = appendToConsumed(actualToken)
                } processNode(newPropertyNode, newConsumed, tokens, currentTokenPosition + 1)
            }
        }
        
        processNode(wildcardProperties, "", StringUtils.split(name, "."), 0)
        
        result.toList
    }

    // For Java callers
    def getPropertiesStartsWith(name: String): JList[String] = propertiesStartsWith(name).asJava

    /**
     * Get a property.
     *
     * @param name      property name
     * @param typ      property type to check against, or null
     * @         property object if found
     */
    private def getProperty(name: String, typ: QName): Property = {
        
        def getPropertyWorker(propertyNode: PropertyNode, tokens: Array[String], currentTokenPosition: Int): Property = {
            if (propertyNode eq null) {
                // Dead end
                null
            } else if (currentTokenPosition == tokens.length) {
                // We're done with the search, see if we found something here
                if (propertyNode.property ne null) propertyNode.property else null
            } else {
                // Dead end
                if (propertyNode.children eq null)
                    return null
                
                val currentToken = tokens(currentTokenPosition)
                
                // Look for value with actual token
                var newNode = propertyNode.children.get(currentToken).orNull
                val result = getPropertyWorker(newNode, tokens, currentTokenPosition + 1)
                if (result ne null)
                    return result
                // If we couldn't find a value with the actual token, look for value with *
                newNode = propertyNode.children.get("*").orNull
                getPropertyWorker(newNode, tokens, currentTokenPosition + 1)
            }
        }
        
        def getExact = exactProperties.get(name)
        
        def getWildcard = Option(getPropertyWorker(wildcardProperties, StringUtils.split(name, "."), 0))
        
        def checkType(p: Property) = 
            if ((typ ne null) && typ != p.typ)
                throw new OXFException("Invalid attribute type requested for property '" + name + "': expected " + typ.getQualifiedName + ", found " + p.typ.getQualifiedName)
            else
                p
        
        getExact orElse getWildcard map checkType orNull
    }
    
    /* All getters */

    private def getPropertyValue(name: String, typ: QName): AnyRef =
        Option(getProperty(name, typ)) map (_.value) orNull

    def getProperty(name: String): Property =
        getProperty(name, null)

    def getObject(name: String): AnyRef =
        getPropertyValue(name, null)

    def getObject(name: String, default: AnyRef): AnyRef =
        Option(getObject(name)) getOrElse default

    def getStringOrURIAsString(name: String, allowEmpty: Boolean = false): String =
        getObject(name) match {
            case p: String ⇒ if (allowEmpty) StringUtils.trimToEmpty(p) else StringUtils.trimToNull(p)
            case p: URI    ⇒ if (allowEmpty) StringUtils.trimToEmpty(p.toString) else StringUtils.trimToNull(p.toString)
            case null      ⇒ null
            case _         ⇒ throw new OXFException("Invalid attribute type requested for property '" + name + "': expected " + XMLConstants.XS_STRING_QNAME.getQualifiedName + " or " + XMLConstants.XS_ANYURI_QNAME.getQualifiedName)
        }

    def getStringOrURIAsString(name: String, default: String, allowEmpty: Boolean): String =
        Option(getStringOrURIAsString(name, allowEmpty)) getOrElse default

    def getString(name: String): String =
        StringUtils.trimToNull(getPropertyValue(name, XMLConstants.XS_STRING_QNAME).asInstanceOf[String])

    def getNmtokens(name: String): JSet[String] =
        getPropertyValue(name, XMLConstants.XS_NMTOKENS_QNAME).asInstanceOf[JSet[String]]

    def getString(name: String, default: String): String =
        Option(getString(name)) getOrElse default

    def getInteger(name: String): JInteger =
        getPropertyValue(name, XMLConstants.XS_INTEGER_QNAME).asInstanceOf[JInteger]

    def getInteger(name: String, default: Int): JInteger =
        Option(getInteger(name)) getOrElse new JInteger(default)

    def getBoolean(name: String): JBoolean =
        getPropertyValue(name, XMLConstants.XS_BOOLEAN_QNAME).asInstanceOf[JBoolean]

    def getBoolean(name: String, default: Boolean): Boolean =
        Option(getBoolean(name)) map (_.booleanValue) getOrElse default

    def getDate(name: String): JDate =
        getPropertyValue(name, XMLConstants.XS_DATE_QNAME).asInstanceOf[JDate]

    def getDateTime(name: String): JDate =
        getPropertyValue(name, XMLConstants.XS_DATETIME_QNAME).asInstanceOf[JDate]

    def getQName(name: String): QName =
        getPropertyValue(name, XMLConstants.XS_QNAME_QNAME).asInstanceOf[QName]

    def getQName(name: String, default: QName): QName =
        Option(getQName(name)) getOrElse default

    def getURI(name: String): URI =
        getPropertyValue(name, XMLConstants.XS_ANYURI_QNAME).asInstanceOf[URI]

    def getNonNegativeInteger(nm: String): JInteger =
        getPropertyValue(nm, XMLConstants.XS_NONNEGATIVEINTEGER_QNAME).asInstanceOf[JInteger]

    def getNCName(nm: String): String =
        getPropertyValue(nm, XMLConstants.XS_NCNAME_QNAME).asInstanceOf[String]

    def getNMTOKEN(nm: String): String =
        getPropertyValue(nm, XMLConstants.XS_NMTOKEN_QNAME).asInstanceOf[String]
}

// Different name to help with Java callers
object JPropertySet {

    case class Property(typ: QName, value: AnyRef, namespaces: Map[String, String]) {

        private var _associatedValue: Option[Any] = None

        def associatedValue[U](evaluate: Property ⇒ U): U = {
            if (_associatedValue.isEmpty)
                _associatedValue = Option(evaluate(this))
            _associatedValue.get.asInstanceOf[U]
        }
    }

    class PropertyNode {
        var property: Property = null
        var children: mutable.Map[String, PropertyNode] = null // token → property node
    }
}