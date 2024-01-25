package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.SecureUtils


trait FormRunnerPlatformJVM extends FormRunnerPlatform {
  def configCheck(): Set[String] = {
    val passwordGeneral         = (! SecureUtils.checkPasswordForKeyUsage(SecureUtils.KeyUsage.General)        )     .set("password.general")
    val passwordToken           = (! SecureUtils.checkPasswordForKeyUsage(SecureUtils.KeyUsage.Token)          )     .set("password.token")
    val passwordFieldEncryption = (! SecureUtils.checkPasswordForKeyUsage(SecureUtils.KeyUsage.FieldEncryption))     .set("password.field-encryption")
    val databaseConfiguration   = (! RelationalUtils.databaseConfigurationPresent(RelationalUtils.newIndentedLogger)).set("database.configuration")

    passwordGeneral ++ passwordToken ++ passwordFieldEncryption ++ databaseConfiguration
  }
}
