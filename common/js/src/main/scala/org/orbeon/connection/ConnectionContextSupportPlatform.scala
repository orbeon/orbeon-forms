package org.orbeon.connection
import org.orbeon.oxf.http.HttpMethod

import java.net.URI


trait ConnectionContextSupportPlatform extends ConnectionContextSupportTrait {

  type ConnectionContext = Unit

  def getContext(extension: Map[String, Any]): Option[Unit] = None

  def withContext[T](
    url          : URI,
    method       : HttpMethod,
    headers      : Map[String, List[String]],
    extension    : Map[String, Any]
  )(
    body         : => T
  )(implicit
    connectionCtx: Option[Unit]
  ): T = body
}