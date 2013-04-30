/**
 * Copyright (C) 2013 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.function.{FunctionSupport, XFormsFunction}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.xforms.XFormsInstance

class XXFormsResource extends XFormsFunction with FunctionSupport {
    override def evaluateItem(xpathContext: XPathContext) = {

        implicit val ctx = xpathContext
        val container = context.container

        def findResourcesElement =
            resolveOrFindByEffectiveId("orbeon-resources") orElse
            resolveOrFindByEffectiveId("fr-form-resources") collect
            { case instance: XFormsInstance ⇒ instance.rootElement }

        def findResourceElementForLang(resourcesElement: NodeInfo, requestedLang: String) = {
            val availableLangs = resourcesElement \ "resource" \@ "lang"
            availableLangs find (_ === requestedLang) orElse availableLangs.headOption flatMap (_.parentOption)
        }

        val resultOpt =
            for {
                resources     ← findResourcesElement
                requestedLang ← Option(XXFormsLang.resolveXMLangHandleAVTs(container, getSourceElement)) // PERF?
                resourceRoot  ← findResourceElementForLang(resources, requestedLang)
                leaf          ← XML.path(resourceRoot, stringArgument(0))
            } yield
                stringToStringValue(leaf.stringValue)

        resultOpt.orNull
    }
}
