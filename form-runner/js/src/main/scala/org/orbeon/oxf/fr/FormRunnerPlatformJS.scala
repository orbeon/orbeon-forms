package org.orbeon.oxf.fr

import org.log4s
import org.orbeon.oxf.fr.permission.Operations


trait FormRunnerPlatformJS extends FormRunnerPlatform{
  def configCheck(): Set[(String, log4s.LogLevel)] = Set.empty
  protected def decryptPrivateModeOperations(encryptedPrivateModeMetadataOpt: Option[String]): Option[Operations] = None
}
