package org.orbeon.oxf.fr


// Offline doesn't support encryption/decryption
object FormRunnerAccessToken extends FormRunnerAccessTokenTrait {
  def encryptToken(tokenDetails: TokenDetails): String = ???
  def decryptToken(token: String): TokenDetails = ???
}