/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis

import controls.ComponentControl
import org.orbeon.oxf.xml.Dom4j
import org.orbeon.oxf.xforms.event.EventHandlerImpl
import org.orbeon.oxf.xforms.XFormsConstants.XXBLScope


trait ShadowChildrenBuilder extends ContainerChildrenBuilder {

    self: ComponentControl ⇒

    // Directly nested handlers (if enabled)
    private def directlyNestedHandlers = {

        // HACK: maybe due to a compiler bug, we cannot access self.element, etc.
        def component = self

        // Inner scope in effect for the component element itself (NOT the shadow tree's scope)
        def innerScope = containerScope

        // Outer scope in effect for the component element itself
        def outerScope =
            if (innerScope.isTopLevelScope)
                innerScope
            else
                component.staticStateContext.partAnalysis.getControlAnalysis(containerScope.fullPrefix.init).scope

        // If enabled, gather elements and annotate them, as they haven't been annotated earlier (because they are
        // nested within the bound element)
        if (binding.abstractBinding.modeHandlers)
            Dom4j.elements(component.element) filter
                (EventHandlerImpl.isEventHandler(_)) map
                    (handlerElement ⇒
                        component.staticStateContext.partAnalysis.xblBindings.annotateSubtreeByElement(
                            component.element,  // bound element
                            handlerElement,     // handler tree to annotate
                            innerScope,         // handler's inner scope is the same as the component's
                            outerScope,         // handler's outer scope is the same as the component's
                            if (component.scope == innerScope) XXBLScope.inner else XXBLScope.outer,
                            binding.innerScope  // handler is within the current component (this determines the prefix of ids)
                    ))
        else
            Seq()
    }
    
    // Return all the children to consider, including relevant shadow tree elements
    override def findRelevantChildrenElements =
        directlyNestedHandlers ++ binding.handlers ++ binding.models :+ binding.compactShadowTree.getRootElement map ((_, binding.innerScope))
}