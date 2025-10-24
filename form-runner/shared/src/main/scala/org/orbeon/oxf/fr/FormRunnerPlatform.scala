package org.orbeon.oxf.fr

import org.log4s
import org.orbeon.oxf.fr.permission.Operations


trait FormRunnerPlatform {
  def configCheck(): Set[(String, log4s.LogLevel)]
  protected def decryptPrivateModeOperations(encryptedPrivateModeMetadataOpt: Option[String]): Option[Operations]
}
