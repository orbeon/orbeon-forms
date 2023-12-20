package org.orbeon.oxf.fr

trait FormRunnerPlatform {
  def configCheck(): Set[String]
}
