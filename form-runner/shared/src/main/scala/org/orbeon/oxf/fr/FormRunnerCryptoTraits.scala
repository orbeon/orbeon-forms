package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.permission.{Operation, Operations}


trait FormRunnerAccessTokenTrait {

  case class TokenHmac(
    app     : String,
    form    : String,
    version : Int,
    document: Option[String]
  )

  case class TokenPayload(
    exp: java.time.Instant, // keep `exp` short for serialization
    ops: List[Operation]    // keep `ops` short for serialization
  )
}

trait FormRunnerOperationsEncryptionTrait {
  def encryptOperations(operationsTokens: Set[String]): String
  def decryptOperations(permissions: String): Option[Operations]
}
