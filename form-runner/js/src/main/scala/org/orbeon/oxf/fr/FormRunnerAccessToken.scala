package org.orbeon.oxf.fr
import org.orbeon.oxf.fr.permission.Operations

import scala.util.Try


// Offline doesn't support encryption/decryption
object FormRunnerAccessToken extends FormRunnerAccessTokenTrait {
  def encryptToken(tokenHmac: TokenHmac, tokenPayload: TokenPayload): Option[String] = None
  def decryptToken(tokenHmac: TokenHmac, token: String): Try[TokenPayload] = ???
  def encryptOperations(operationsTokens: Set[String]): String = ???
  def decryptOperations(permissions: String): Option[Operations] = ???
}