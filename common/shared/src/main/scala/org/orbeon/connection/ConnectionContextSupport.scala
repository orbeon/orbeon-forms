package org.orbeon.connection

import org.orbeon.oxf.http.HttpMethod

import java.net.URI


trait ConnectionContextSupportTrait {

  type ConnectionContext

  def getContext(extension: Map[String, Any]): Option[ConnectionContext]

  def withContext[T](
    url      : URI,
    method   : HttpMethod,
    headers  : Map[String, List[String]],
    extension: Map[String, Any]
  )(
    body     : => T
  )(implicit
    connectionCtx: Option[ConnectionContext]
  ): T
}

object ConnectionContextSupport extends ConnectionContextSupportPlatform with ConnectionContextSupportTrait