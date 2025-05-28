package org.orbeon.oxf.util

import org.orbeon.connection.ConnectionContextProvider
import org.orbeon.oxf.webapp.ProcessorService

import java.net.URI
import java.util as ju


case class BuiltinClientConnectionContext(ps: Option[ProcessorService])

class BuiltinClientConnectionContextProvider extends ConnectionContextProvider[BuiltinClientConnectionContext] {

  def getContext(extension: ju.Map[String, Any]): BuiltinClientConnectionContext =
    BuiltinClientConnectionContext(ProcessorService.currentProcessorService.value)

  def pushContext(
    ctx      : BuiltinClientConnectionContext,
    url      : URI,
    method   : String,
    headers  : ju.Map[String, Array[String]],
    extension: ju.Map[String, Any]
  ): Unit =
    ctx.ps.foreach(ps => ProcessorService.currentProcessorService.value = ps)

  def popContext(ctx: BuiltinClientConnectionContext): Unit =
    ProcessorService.currentProcessorService.clear()
}
