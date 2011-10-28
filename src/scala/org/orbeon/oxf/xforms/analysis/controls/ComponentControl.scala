package org.orbeon.oxf.xforms.analysis.controls

import org.dom4j.Element
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.xbl.Scope

class ComponentControl(staticStateContext: StaticStateContext, element: Element, parent: Option[ElementAnalysis], preceding: Option[ElementAnalysis], scope: Scope)
        extends ContainerControl(staticStateContext, element, parent, preceding, scope) {

    val binding = staticStateContext.partAnalysis.xblBindings.getBinding(prefixedId)

    // TODO: TEMP: Control does not have an XPath binding. But return one anyway so that controls w/o their own binding also get updated.
    override protected def computeBindingAnalysis = getContextAnalysis
}