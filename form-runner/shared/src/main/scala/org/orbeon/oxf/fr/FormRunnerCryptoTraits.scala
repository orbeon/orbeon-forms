package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.permission.{Operation, Operations}


trait FormRunnerAccessTokenTrait extends FormRunnerTokenTrait {

  def encryptToken(
    app        : String,
    form       : String,
    version    : Int,
    documentOpt: Option[String],
    validity   : java.time.Duration,
    operations : Set[Operation]
  ): Option[String]

  type TokenPayloadType = List[Operation]
}

trait FormRunnerAdminTokenTrait extends FormRunnerTokenTrait {

  def encryptToken(
    validity           : java.time.Duration,
    isInternalAdminUser: TokenPayloadType
  ): Option[String]

  type TokenPayloadType = Boolean
}

trait FormRunnerTokenTrait {

  type TokenPayloadType

  case class TokenPayload(
    exp: java.time.Instant, // keep `exp` short for serialization
    ops: TokenPayloadType   // keep `ops` short for serialization
  )
}

trait FormRunnerOperationsEncryptionTrait {
  def encryptOperations(operationsTokens: Set[String]): String
  def decryptOperations(permissions: String): Option[Operations]

  def encryptString(value: String): String
  def decryptString(value: String): Option[String]
}
