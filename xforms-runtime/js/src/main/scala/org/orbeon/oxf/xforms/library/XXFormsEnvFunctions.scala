package org.orbeon.oxf.xforms.library

import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.model.XFormsInstance
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om


trait XXFormsEnvFunctions extends OrbeonFunctionLibrary {

  @XPathFunction
  def instance(
    instanceId : String)(implicit
    xpc        : XPathContext,
    xfc        : XFormsFunction.Context
  ): Option[om.NodeInfo] =
    instanceId.trimAllToOpt flatMap (XFormsInstance.findInAncestorScopes(xfc.container, _))
}
