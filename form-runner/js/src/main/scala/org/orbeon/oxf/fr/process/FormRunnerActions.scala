package org.orbeon.oxf.fr.process

import org.orbeon.oxf.fr.process.ProcessInterpreter.Action


trait FormRunnerActions extends FormRunnerActionsCommon {

  self: XFormsActions => // for `tryCallback`

  val AllowedFormRunnerActions: Map[String, Action] =
    CommonAllowedFormRunnerActions
}