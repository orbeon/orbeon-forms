package org.orbeon.connection

import java.net.URI
import java.{util => ju}


case class DemoConnectionContext(tid: Long)

class DemoConnectionContextProvider extends ConnectionContextProvider[DemoConnectionContext] {

  def getContext(extension: ju.Map[String, Any]): DemoConnectionContext = {
    val ctx = DemoConnectionContext(Thread.currentThread().getId)
    println(s"getContext: $extension, returning: $ctx")
    ctx
  }

  def pushContext(
    ctx      : DemoConnectionContext,
    url      : URI,
    method   : String,
    headers  : ju.Map[String, Array[String]],
    extension: ju.Map[String, Any]
  ): Unit =
    println(s"pushCtx: $ctx, url: $url, method: $method, headers: $headers, extension: $extension")

  def popContext(ctx: DemoConnectionContext): Unit =
    println(s"popCtx: $ctx")
}
