package org.orbeon.oxf.fr.process

import org.orbeon.oxf.fr.process.ProcessInterpreter.Action


trait FormRunnerActions extends FormRunnerActionsCommon {
  val AllowedFormRunnerActions: Map[String, Action] =
    CommonAllowedFormRunnerActions
}