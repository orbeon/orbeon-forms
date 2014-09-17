/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xml

import org.apache.commons.lang3.StringUtils
import org.orbeon.saxon.om.{NodeInfo, Item, ValueRepresentation, Name10Checker}
import org.orbeon.saxon.value.{AtomicValue, StringValue, Value}
import org.orbeon.scaxon.XML


object SaxonUtils {

    // Make an NCName out of a non-blank string
    // Any characters that do not belong in an NCName are converted to '_'.
    def makeNCName(name: String): String = {

        require(StringUtils.isNotBlank(name), "name must not be blank or empty")

        val name10Checker = Name10Checker.getInstance
        if (name10Checker.isValidNCName(name)) {
            name
        } else {
            val sb = new StringBuilder
            val start = name.charAt(0)

            sb.append(if (name10Checker.isNCNameStartChar(start)) start else '_')
            for (i ← 1 until name.length) {
                val ch = name.charAt(i)
                sb.append(if (name10Checker.isNCNameChar(ch)) ch else '_')
            }
            sb.toString
        }
    }

    def compareValueRepresentations(valueRepr1: ValueRepresentation, valueRepr2: ValueRepresentation): Boolean =
        (valueRepr1, valueRepr2) match {
            // Ideally we wouldn't support null here (XFormsVariableControl passes null)
            case (null,             null)            ⇒ true
            case (null,             _)               ⇒ false
            case (_,                null)            ⇒ false
            case (v1: Value, v2: Value)              ⇒ compareValues(v1, v2)
            case (v1: NodeInfo, v2: NodeInfo)        ⇒ v1 == v2
            // 2014-08-18: Checked Saxon class hierarchy
            // Saxon type hierarchy is closed (ValueRepresentation = NodeInfo | Value)
            case _                                   ⇒ throw new IllegalStateException
        }
    
    def compareValues(value1: Value, value2: Value): Boolean = {
        val iter1 = XML.asScalaIterator(value1.iterate)
        val iter2 = XML.asScalaIterator(value2.iterate)

        iter1.zipAll(iter2, null, null) forall (compareItems _).tupled
    }
    
    // Whether two sequences contain identical items
    def compareItemSeqs(nodeset1: Seq[Item], nodeset2: Seq[Item]): Boolean =
        nodeset1.size == nodeset2.size &&
            (nodeset1.iterator.zip(nodeset2.iterator) forall (compareItems _).tupled)

    def compareItems(item1: Item, item2: Item): Boolean =
        (item1, item2) match {
            // We probably shouldn't support null at all here!
            case (null,             null)            ⇒ true
            case (null,             _)               ⇒ false
            case (_,                null)            ⇒ false
            // StringValue.equals() throws (Saxon equality requires a collation)
            case (v1: StringValue,  v2: StringValue) ⇒ v1.codepointEquals(v2)
            case (v1: StringValue,  v2 )             ⇒ false
            case (v1,               v2: StringValue) ⇒ false
            // AtomicValue.equals() may throw (Saxon changes the standard equals() contract)
            case (v1: AtomicValue,  v2: AtomicValue) ⇒ v1 == v2
            case (v1,               v2: AtomicValue) ⇒ false
            case (v1: AtomicValue,  v2)              ⇒ false
            // NodeInfo
            case (v1: NodeInfo,     v2: NodeInfo)    ⇒ v1 == v2
            // 2014-08-18: Checked Saxon class hierarchy
            // Saxon type hierarchy is closed (Item = NodeInfo | AtomicValue)
            case _                                   ⇒ throw new IllegalStateException
        }
}
