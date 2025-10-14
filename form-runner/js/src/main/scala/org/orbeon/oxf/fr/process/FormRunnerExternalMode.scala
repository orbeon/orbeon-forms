package org.orbeon.oxf.fr.process

import org.orbeon.oxf.fr.FormRunnerParams


object FormRunnerExternalMode extends FormRunnerExternalModeTrait {

  def createTokenAndStoreState(
    modeState         : ModeState,
    expirationDuration: java.time.Duration
  )(implicit
    formRunnerParams: FormRunnerParams
  ): String =
    throw new UnsupportedOperationException("external mode not supported in this environment")
}
