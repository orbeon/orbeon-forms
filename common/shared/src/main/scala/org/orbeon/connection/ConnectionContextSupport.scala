package org.orbeon.connection

import org.orbeon.oxf.http.HttpMethod

import java.net.URI


trait ConnectionContextSupportTrait {

  type ConnectionContext
  type ConnectionContexts = List[Option[ConnectionContext]]

  def EmptyConnectionContexts: ConnectionContexts

  def findContext(extension: Map[String, Any]): ConnectionContexts

  def withContext[T](
    url      : URI,
    method   : HttpMethod,
    headers  : Map[String, List[String]],
    extension: Map[String, Any]
  )(
    body     : => T
  )(implicit
    connectionCtx: ConnectionContexts
  ): T
}

object ConnectionContextSupport extends ConnectionContextSupportPlatform with ConnectionContextSupportTrait {
  val EmptyConnectionContexts: ConnectionContexts = Nil
}