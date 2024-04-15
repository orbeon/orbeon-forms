package org.orbeon.oxf.fr

import org.log4s


trait FormRunnerPlatformJS extends FormRunnerPlatform{
  def configCheck(): Set[(String, log4s.LogLevel)] = Set.empty
}
