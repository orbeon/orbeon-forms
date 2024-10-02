package org.orbeon.connection
import org.orbeon.oxf.http.HttpMethod

import java.net.URI


// 2024-10-02: For now we don't pass any connection context in the JavaScript environment.
trait ConnectionContextSupportPlatform extends ConnectionContextSupportTrait {

  type ConnectionContext = Unit

  def findContext(extension: Map[String, Any]): Option[Unit] = None

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