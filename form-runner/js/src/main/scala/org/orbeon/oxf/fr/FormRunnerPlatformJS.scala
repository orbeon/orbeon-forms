package org.orbeon.oxf.fr

trait FormRunnerPlatformJS extends FormRunnerPlatform{
  def configCheck(): Set[String] = Set.empty
}
