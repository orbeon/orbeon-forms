package org.orbeon.oxf.xml

import org.orbeon.saxon.pattern.NodeKindTest
import org.orbeon.saxon.`type`.Type
import org.orbeon.saxon.om.{EmptyIterator, Axis, AxisIterator, NodeInfo}

/**
 * This Iterator returns a node's attributes and descendant nodes and attributes.
 *
 * It is based on the Saxon Navigator DescendantEnumeration, simplified and rewritten in Scala.
 */
class AttributesAndElementsIterator(start: NodeInfo, includeSelf: Boolean = true) extends Iterator[NodeInfo] {

    private var current = findNext()

    def next() = {
        val result = current
        current = findNext()
        result
    }

    def hasNext = current ne null

    private var attributes: AxisIterator = _
    private var descendants: Iterator[NodeInfo] = _
    private var children: AxisIterator = _

    private def findNext(): NodeInfo = {

        // Exhaust attributes if any
        if (attributes ne null) {
            val next = attributes.next().asInstanceOf[NodeInfo]
            if (next ne null)
                return next
            else
                attributes = null
        }

        // Exhaust descendants if any
        if (descendants ne null) {
            if (descendants.hasNext)
                return descendants.next()
            else
                descendants = null
        }

        // We have exhausted attributes and descendants
        if (children ne null) {
            // Move to next child
            val next = children.next().asInstanceOf[NodeInfo]
            if (next ne null) {
                attributes = next.iterateAxis(Axis.ATTRIBUTE);
                if (next.hasChildNodes)
                    descendants = new AttributesAndElementsIterator(next, false)
                next
            } else
                null
        } else {
            // This is the start
            attributes = start.iterateAxis(Axis.ATTRIBUTE);

            children =
                if (start.hasChildNodes)
                    start.iterateAxis(Axis.CHILD, NodeKindTest.makeNodeKindTest(Type.ELEMENT))
                else
                    EmptyIterator.getInstance

            if (includeSelf)
                start
            else
                findNext()
        }
    }
}

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Michael H. Kay.
//
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved.
//
// Contributor(s): none.
//

