package org.orbeon.oxf.fr
import org.orbeon.oxf.fr.permission.Operations

import scala.util.Try


// Offline doesn't support encryption/decryption
object FormRunnerAccessToken extends FormRunnerAccessTokenTrait {
  def encryptToken(tokenDetails: TokenDetails): Option[String] = None
  def decryptToken(token: String): Try[TokenDetails] = ???
  def encryptOperations(operationsTokens: Set[String]): String = ???
  def decryptOperations(permissions: String): Option[Operations] = ???
}