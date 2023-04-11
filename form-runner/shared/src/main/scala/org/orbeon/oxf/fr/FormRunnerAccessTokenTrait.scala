package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.permission.Operations

import scala.util.Try


trait FormRunnerAccessTokenTrait {

  case class TokenHmac(
    app     : String,
    form    : String,
    version : Int,
    document: Option[String]
  )

  case class TokenPayload(
   exp: java.time.Instant // keep `exp`
 )

  def encryptToken(tokenHmac: TokenHmac, tokenPayload: TokenPayload): Option[String]
  def decryptToken(tokenHmac: TokenHmac, token: String): Try[TokenPayload]

  def encryptOperations(operationsTokens: Set[String]): String
  def decryptOperations(permissions: String): Option[Operations]
}
