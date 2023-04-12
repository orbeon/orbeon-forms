package org.orbeon.oxf.fr

import org.orbeon.io.CharsetNames
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.fr.permission.Operations
import org.orbeon.oxf.util.SecureUtils
import org.orbeon.oxf.util.StringUtils._

import scala.util.Try


// CE doesn't support encryption/decryption
object FormRunnerAccessToken extends FormRunnerAccessTokenTrait {

  def encryptToken(tokenHmac: TokenHmac, tokenPayload: TokenPayload): Option[String] = {
    Version.instance.requirePEFeature("Token-based authentication")
    None
  }

  def decryptToken(tokenHmac: TokenHmac, token: String): Try[TokenPayload] = {
    Version.instance.requirePEFeature("Token-based authentication")
    throw new UnsupportedOperationException
  }

  def encryptOperations(operationsTokens: Set[String]): String =
    SecureUtils.encrypt(operationsTokens.mkString(" ").getBytes(CharsetNames.Utf8))

  def decryptOperations(operationsString: String): Option[Operations] =
    operationsString.trimAllToOpt.flatMap(s => Operations.parseFromString(new String(SecureUtils.decrypt(s), CharsetNames.Utf8)))
}
