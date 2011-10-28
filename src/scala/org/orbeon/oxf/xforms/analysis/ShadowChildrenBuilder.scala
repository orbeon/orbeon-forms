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
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import collection.JavaConverters._
import org.orbeon.oxf.xforms.xbl.Scope


trait ShadowChildrenBuilder extends ContainerChildrenBuilder {

    this: ComponentControl ⇒

    // Return all the children of the shadow tree that need to be built
    override def findRelevantChildrenElements =
        binding.handlers ++ binding.models ++ Dom4jUtils.elements(binding.compactShadowTree.getRootElement).asScala

    // Change the scope to the binding's inner scope
    override def buildChildren(build: Builder, containerScope: Scope) =
        super.buildChildren(build, binding.innerScope)
}