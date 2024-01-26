package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.permission.{Operation, Operations}


// Offline doesn't support encryption/decryption
object FormRunnerAccessToken extends FormRunnerAccessTokenTrait {
  def encryptToken(
    app        : String,
    form       : String,
    version    : Int,
    documentOpt: Option[String],
    validity   : java.time.Duration,
    operations : Set[Operation]
  ): Option[String] = None
}

object FormRunnerAdminToken extends FormRunnerAdminTokenTrait {
  def encryptToken(
    validity           : java.time.Duration,
    isInternalAdminUser: Boolean
  ): Option[String] = None
}

object FormRunnerOperationsEncryption extends FormRunnerOperationsEncryptionTrait {
  def encryptOperations(operationsTokens: Set[String]): String = ???
  def decryptOperations(permissions: String): Option[Operations] = ???

  def encryptString(value: String): String = ???
  def decryptString(value: String): Option[String] = ???
}