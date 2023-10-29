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
}

object FormRunnerOperationsEncryption extends FormRunnerOperationsEncryptionTrait {

  def encryptOperations(operationsTokens: Set[String]): String =
    SecureUtils.encrypt(
      keyUsage = SecureUtils.KeyUsage.General,
      bytes    = operationsTokens.mkString(" ").getBytes(CharsetNames.Utf8)
    )

  def decryptOperations(operationsString: String): Option[Operations] =
    operationsString.trimAllToOpt.flatMap(nonBlankOperationsString =>
      Operations.parseFromString(new String(
        SecureUtils.decrypt(
          keyUsage = SecureUtils.KeyUsage.General,
          text     = nonBlankOperationsString
        ),
        CharsetNames.Utf8
      ))
    )

  def encryptString(value: String): String =
    SecureUtils.encrypt(
      keyUsage = SecureUtils.KeyUsage.General,
      bytes    = value.getBytes(CharsetNames.Utf8)
    )

  def decryptString(value: String): Option[String] =
    value.trimAllToOpt.map(nonBlankValue =>
      new String(
        SecureUtils.decrypt(
          keyUsage = SecureUtils.KeyUsage.General,
          text     = nonBlankValue
        ),
        CharsetNames.Utf8
      )
    )
}