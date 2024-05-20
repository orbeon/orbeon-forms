package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.analysis.controls.{LHHA, StaticLHHASupport}
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.function.XFormsFunction.relevantControl
import org.orbeon.saxon.expr.XPathContext
import shapeless.syntax.typeable._


object LHHAFunctionSupport {

  def labelHintAppearance(
    controlId: String,
    lhha     : LHHA
  )(implicit
    xpc      : XPathContext,
    xfc      : XFormsFunction.Context
  ): Option[String] =
    relevantControl(controlId)
      .map(_.staticControl)
      .flatMap(_.cast[StaticLHHASupport])
      .flatMap(_.anyByOpt(lhha))
      .collect {
        case lh if lh.isPlaceholder => "minimal"
        case _                      => "full"
      }
}
