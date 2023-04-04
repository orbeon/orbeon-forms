package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.permission.Operations
import org.orbeon.oxf.util.SecureUtils
import org.orbeon.oxf.util.StringUtils._


// CE doesn't support encryption/decryption
object FormRunnerAccessToken extends FormRunnerAccessTokenTrait {

  def encryptToken(tokenDetails: TokenDetails): String = ???
  def decryptToken(token: String): TokenDetails = ???

  def encryptOperations(operationsTokens: Set[String]): String =
    SecureUtils.encrypt(operationsTokens.mkString(" ").getBytes("UTF-8"))

  def decryptOperations(operationsString: String): Option[Operations] =
    operationsString.trimAllToOpt.flatMap(s => Operations.parseFromString(new String(SecureUtils.decrypt(s), "UTF-8")))
}
