package org.orbeon.oxf.fr

import org.log4s


trait FormRunnerPlatform {
  def configCheck(): Set[(String, log4s.LogLevel)]
}
