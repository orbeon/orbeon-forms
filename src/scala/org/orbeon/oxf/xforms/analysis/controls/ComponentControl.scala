package org.orbeon.oxf.xforms.analysis.controls

import org.dom4j.Element
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase

class ComponentControl(staticStateContext: StaticStateContext, element: Element, parent: ContainerTrait, preceding: Option[ElementAnalysis], scope: XBLBindingsBase.Scope)
        extends ContainerControl(staticStateContext, element, parent, preceding, scope) {

    // TODO: TEMP: Control does not have a binding. But return one anyway so that controls w/o their own binding also get updated.
    override protected def computeBindingAnalysis = getContextAnalysis

    // No value
    override protected def computeValueAnalysis = None
}