package org.orbeon.oxf.fr

import org.log4s
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, SecureUtils}


trait FormRunnerPlatformJVM extends FormRunnerPlatform {
  def configCheck(): Set[(String, log4s.LogLevel)] = {

    implicit val logger: IndentedLogger = RelationalUtils.newIndentedLogger

    val passwordGeneral         = (! SecureUtils.checkPasswordForKeyUsage(SecureUtils.KeyUsage.General)        )       .set("password.general",          log4s.Error)
    val passwordToken           = (! SecureUtils.checkPasswordForKeyUsage(SecureUtils.KeyUsage.Token)          )       .set("password.token",            log4s.Info)
    val passwordFieldEncryption = (! SecureUtils.checkPasswordForKeyUsage(SecureUtils.KeyUsage.FieldEncryption))       .set("password.field-encryption", log4s.Info)
    val databaseConfiguration   = (! RelationalUtils.databaseConfigurationPresent(CoreCrossPlatformSupport.properties)).set("database.configuration",    log4s.Error)

    passwordGeneral ++ passwordToken ++ passwordFieldEncryption ++ databaseConfiguration
  }
}
