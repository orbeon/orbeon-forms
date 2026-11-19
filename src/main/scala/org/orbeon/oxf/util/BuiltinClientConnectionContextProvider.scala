package org.orbeon.oxf.util

import org.orbeon.connection.ConnectionContextProvider
import org.orbeon.oxf.webapp.ProcessorService

import java.net.URI
import java.util as ju


case class BuiltinClientConnectionContext(ps: Option[ProcessorService])

class BuiltinClientConnectionContextProvider extends ConnectionContextProvider[BuiltinClientConnectionContext] {

  private val previousValues = new ThreadLocal[List[Option[ProcessorService]]]

  def getContext(extension: ju.Map[String, Any]): BuiltinClientConnectionContext =
    BuiltinClientConnectionContext(ProcessorService.currentProcessorService.value)

  def pushContext(
    ctx      : BuiltinClientConnectionContext,
    url      : URI,
    method   : String,
    headers  : ju.Map[String, Array[String]],
    extension: ju.Map[String, Any]
  ): Unit = {
    val prev = ProcessorService.currentProcessorService.value
    val tail = Option(previousValues.get()).getOrElse(Nil)
    previousValues.set(prev :: tail)
    ctx.ps match {
      case Some(ps) => ProcessorService.currentProcessorService.value = ps
      case None     => ProcessorService.currentProcessorService.clear()
    }
  }

  def popContext(ctx: BuiltinClientConnectionContext): Unit =
    Option(previousValues.get()) match {
      case Some(prev :: tail) =>
        if (tail.isEmpty)
          previousValues.remove()
        else
          previousValues.set(tail)
        prev match {
          case Some(ps) => ProcessorService.currentProcessorService.value = ps
          case None     => ProcessorService.currentProcessorService.clear()
        }
      case _ =>
        previousValues.remove()
        ProcessorService.currentProcessorService.clear()
    }
}
