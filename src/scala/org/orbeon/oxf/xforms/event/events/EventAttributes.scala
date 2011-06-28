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
package org.orbeon.oxf.xforms.event.events

import org.orbeon.saxon.om.SingletonIterator
import org.orbeon.saxon.value.StringValue
import org.orbeon.oxf.xforms.event.XFormsEvent

trait EventAttributes extends XFormsEvent {

    // Implementing class must specify attributes
    protected val attributes: Map[String, () => String]

    private def stringIterator(value: String) = SingletonIterator.makeIterator(StringValue.makeStringValue(value))

    override def getAttribute(name: String) = attributes(name) match {
        case null => super.getAttribute(name)
        case getvalue => stringIterator(getvalue())
    }

    def toStringArray =
         attributes.keys.toArray flatMap
            (name => Array(name, getAttribute(name).next().getStringValue))
}