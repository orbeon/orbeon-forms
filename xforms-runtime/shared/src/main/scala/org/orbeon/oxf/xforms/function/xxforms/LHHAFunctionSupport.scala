package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.analysis.controls.{LHHA, StaticLHHASupport}
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.function.XFormsFunction.relevantControl
import org.orbeon.saxon.expr.XPathContext
import shapeless.syntax.typeable._


object LHHAFunctionSupport {

  // This returns a control's LHHA value, if it has one. It also handles the case where the LHHA is obtained from a
  // referencing control via `label-for`.
  // See https://github.com/orbeon/orbeon-forms/issues/6333
  def lhhaValue(
    controlId: String,
    lhha     : LHHA
  )(implicit
    xpc      : XPathContext,
    xfc      : XFormsFunction.Context
  ): Option[String] =
    relevantControl(controlId)
      .flatMap(_.lhhaValue(lhha))

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
      .flatMap(_.directOrByLhhOpt(lhha))
      .collect {
        case lh if lh.isPlaceholder => "minimal"
        case _                      => "full"
      }
}
