package org.orbeon.oxf.fr
import org.orbeon.oxf.fr.permission.Operations


// Offline doesn't support encryption/decryption
object FormRunnerAccessToken extends FormRunnerAccessTokenTrait {
  def encryptToken(tokenDetails: TokenDetails): String = ???
  def decryptToken(token: String): TokenDetails = ???
  def encryptOperations(operationsTokens: Set[String]): String = ???
  def decryptOperations(permissions: String): Option[Operations] = ???
}