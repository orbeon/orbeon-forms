package org.orbeon.oxf.fr


trait FormRunnerAccessTokenTrait {

  private def noPipe(s: String) = ! s.contains('|')

  case class TokenDetails(app: String, form: String, version: Int, documentOpt: Option[String], expiration: java.time.Instant) {

    require(noPipe(app) && noPipe(form) && documentOpt.forall(noPipe))

    def toParts: List[String] =
      app :: form :: version.toString :: documentOpt.toList ::: expiration.toString :: Nil
  }

  def encryptToken(tokenDetails: TokenDetails): String
  def decryptToken(token: String): TokenDetails
}
