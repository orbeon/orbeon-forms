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
package org.orbeon.oxf.xforms.xbl

import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.dom4j.Document
import org.orbeon.oxf.xforms._
import analysis.model.Model

case class ConcreteBinding(
    innerScope: XBLBindingsBase.Scope,  // each binding defines a new scope
    fullShadowTree: Document,           // with full content, e.g. XHTML
    compactShadowTree: Document,        // without full content, only the XForms controls
    models: Seq[Model],                 // all the models
    bindingId: String,
    containerElementName: String        // "div" by default
)

object ConcreteBinding {
    // Construct a ConcreteBinding
    def apply(abstractBinding: AbstractBinding, innerScope: XBLBindingsBase.Scope, fullShadowTree: Document, compactShadowTree: Document, models: Seq[Model]) = {

        assert(abstractBinding.bindingId.isDefined, "missing id on XBL binding for " + Dom4jUtils.elementToDebugString(abstractBinding.bindingElement))

        val containerElementName =
            Option(abstractBinding.bindingElement.attributeValue(XFormsConstants.XXBL_CONTAINER_QNAME)) getOrElse
                "div"

        new ConcreteBinding(innerScope, fullShadowTree, compactShadowTree, models, abstractBinding.bindingId.get, containerElementName)
    }
}