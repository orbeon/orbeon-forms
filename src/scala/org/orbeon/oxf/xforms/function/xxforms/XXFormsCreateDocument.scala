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
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.oxf.util.XPathCache

class XXFormsCreateDocument extends XFormsFunction  {
    // Create a new DocumentWrapper. If we use a global one, the first document ever created is wrongly returned!
    override def evaluateItem(xpathContext: XPathContext) =
        new DocumentWrapper(Dom4jUtils.createDocument, null, XPathCache.getGlobalConfiguration)
}