package org.orbeon.oxf.fr

import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.fr.permission.{Operation, Operations}
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{ByteEncoding, SecureUtils}

import scala.util.{Failure, Success, Try}


object FormRunnerAccessToken extends FormRunnerAccessTokenTrait with FormRunnerTokenJvmTrait {

  import io.circe.generic.auto._

  case class TokenHmac(
    app     : String,
    form    : String,
    version : Int,
    document: Option[String]
  )

  def encryptToken(
    app        : String,
    form       : String,
    version    : Int,
    documentOpt: Option[String],
    validity   : java.time.Duration,
    operations : Set[Operation]
  ): Option[String] =
    encryptToken(
      TokenHmac(
        app      = app,
        form     = form,
        version  = version,
        document = documentOpt
      ),
      TokenPayload(
        exp = java.time.Instant.now.plus(validity),
        ops = Operations.inDefinitionOrder(operations)
      ),
      SecureUtils.KeyUsage.Token
    )

  def decryptToken(tokenHmac: TokenHmac, token: String): Try[TokenPayload] =
    decryptToken(tokenHmac, token, SecureUtils.KeyUsage.Token)
}

object FormRunnerAdminToken extends FormRunnerAdminTokenTrait with FormRunnerTokenJvmTrait {

  import io.circe.generic.auto._

  type TokenHmac = Unit

  def encryptToken(
    validity           : java.time.Duration,
    isInternalAdminUser: Boolean
  ): Option[String] =
    encryptToken(
      (),
      TokenPayload(
        exp = java.time.Instant.now.plus(validity),
        ops = isInternalAdminUser
      ),
      SecureUtils.KeyUsage.GeneralNoCheck
    )

  def decryptToken(tokenHmac: Unit, token: String): Try[TokenPayload] =
    decryptToken(tokenHmac, token, SecureUtils.KeyUsage.GeneralNoCheck)
}

trait FormRunnerTokenJvmTrait extends FormRunnerTokenTrait {

  type TokenHmac

  def decryptToken(tokenHmac: TokenHmac, token: String): Try[TokenPayload]

  def decryptTokenPayloadCheckExpiration(tokenHmac: TokenHmac, token: String): Try[TokenPayloadType] =
    decryptToken(tokenHmac, token) map { tokenPayload =>
      if (tokenPayload.exp.isAfter(java.time.Instant.now))
        tokenPayload.ops
      else
        throw HttpStatusCodeException(StatusCode.Forbidden)
    }

  protected def encryptToken(
    tokenHmac   : TokenHmac,
    tokenPayload: TokenPayload,
    keyUsage    : SecureUtils.KeyUsage
  )(implicit
    en1: Encoder[TokenPayload],
    en2: Encoder[TokenHmac]
  ): Option[String] = {
    val toEncryptJsonString = tokenPayload.asJson.noSpaces
    Some(s"${createTokenHmac(tokenHmac, keyUsage)}.${SecureUtils.encrypt(keyUsage, toEncryptJsonString.getBytes(CharsetNames.Utf8))}")
  }

  protected def decryptToken(
    tokenHmac: TokenHmac,
    token    : String,
    keyUsage : SecureUtils.KeyUsage
  )(implicit
    en: Encoder[TokenHmac],
    de: Decoder[TokenPayload]
  ): Try[TokenPayload] =
    token.splitTo[List](".") match {
      case hmac :: encrypted :: Nil =>
        if (createTokenHmac(tokenHmac, keyUsage) != hmac)
          Failure(new IllegalArgumentException("Invalid token"))
        else
          Try(new String(SecureUtils.decrypt(keyUsage, encrypted), CharsetNames.Utf8)) flatMap { decrypted =>
            io.circe.parser.decode[TokenPayload](decrypted).fold(Failure.apply, Success.apply)
          }
      case _ =>
        Failure(new IllegalArgumentException("Invalid token"))
    }

  // Use HMAC instead of just a hash. Even though the payload is encrypted, an attacker could simply recreate a plain
  // hash with the knowledge of the encoding. HMAC prevents this.
  private def createTokenHmac(
    tokenHmac: TokenHmac,
    keyUsage : SecureUtils.KeyUsage
  )(implicit
    en: Encoder[TokenHmac]
  ): String =
    SecureUtils.hmacString(
      keyUsage = keyUsage,
      text     = tokenHmac.asJson.noSpaces,
      encoding = ByteEncoding.Base64
    )
}

object FormRunnerOperationsEncryption extends FormRunnerOperationsEncryptionTrait {

  // The following operations use `KeyUsage.Weak` as they are only used to encrypt and decrypt parameter for
  // mode changes and the like. They are not used to encrypt sensitive data. `KeyUsage.Weak` is meant to be
  // used only temporarily, until the installation is setup with a proper crypto password.

  def encryptOperations(operationsTokens: Set[String]): String =
    SecureUtils.encrypt(
      keyUsage = SecureUtils.KeyUsage.GeneralNoCheck,
      bytes    = operationsTokens.mkString(" ").getBytes(CharsetNames.Utf8)
    )

  def decryptOperations(operationsString: String): Option[Operations] =
    operationsString.trimAllToOpt.flatMap(nonBlankOperationsString =>
      Operations.parseFromString(new String(
        SecureUtils.decrypt(
          keyUsage = SecureUtils.KeyUsage.GeneralNoCheck,
          text     = nonBlankOperationsString
        ),
        CharsetNames.Utf8
      ))
    )

  // TODO: Find a better location for these methods as they are not specific to operations.
  def encryptString(value: String): String =
    SecureUtils.encrypt(
      keyUsage = SecureUtils.KeyUsage.GeneralNoCheck,
      bytes    = value.getBytes(CharsetNames.Utf8)
    )

  def decryptString(value: String): Option[String] =
    value.trimAllToOpt.map(nonBlankValue =>
      new String(
        SecureUtils.decrypt(
          keyUsage = SecureUtils.KeyUsage.GeneralNoCheck,
          text     = nonBlankValue
        ),
        CharsetNames.Utf8
      )
    )
}