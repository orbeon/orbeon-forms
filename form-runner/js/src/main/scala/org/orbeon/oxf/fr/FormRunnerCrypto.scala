package org.orbeon.oxf.fr
import org.orbeon.oxf.fr.permission.Operations

import scala.util.Try


// Offline doesn't support encryption/decryption
object FormRunnerAccessToken extends FormRunnerAccessTokenTrait {
  def encryptToken(tokenHmac: TokenHmac, tokenPayload: TokenPayload): Option[String] = None
  def decryptToken(tokenHmac: TokenHmac, token: String): Try[TokenPayload] = ???
}

object FormRunnerOperationsEncryption extends FormRunnerOperationsEncryptionTrait {
  def encryptOperations(operationsTokens: Set[String]): String = ???
  def decryptOperations(permissions: String): Option[Operations] = ???

  def encryptString(value: String): String = ???
  def decryptString(value: String): Option[String] = ???
}