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


trait ShadowChildrenBuilder extends ContainerChildrenBuilder {

    self: ComponentControl ⇒

    // Directly nested handlers (if enabled)
    private def directlyNestedHandlers =
        if (binding.abstractBinding.modeHandlers)
            Dom4j.elements(_element) filter
                (EventHandlerImpl.isEventHandler(_)) map
                    (e ⇒ (
                        _staticStateContext.partAnalysis.xblBindings.annotateSubtreeByElement(
                            _element,               // bound element
                            e,                      // handler tree to annotate
                            containerScope,         // handler's inner scope is the scope of the container in which the current component is
                            _scope,                 // TODO: actual outer scope! (this works only if the current component is itself in the outer scope)
                            binding.innerScope),    // handler is within the current component
                        binding.innerScope
                    ))
        else
            Seq()
    
    // Return all the children to consider, including relevant shadow tree elements
    override def findRelevantChildrenElements =
        directlyNestedHandlers ++ (binding.handlers ++ binding.models :+ binding.compactShadowTree.getRootElement map ((_, binding.innerScope)))
}