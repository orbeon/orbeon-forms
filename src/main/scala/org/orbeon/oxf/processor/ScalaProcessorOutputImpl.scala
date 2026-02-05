package org.orbeon.oxf.processor

import org.orbeon.oxf.cache.OutputCacheKey
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xml.XMLReceiver

import scala.util.chaining.*


// Utility class to simplify writing Scala-based `ProcessorOutput`s
abstract class ScalaProcessorOutputImpl(processor: ProcessorImpl, name: String)
  extends org.orbeon.oxf.processor.impl.ProcessorOutputImpl(processor, name) {

  type ValidityType
  type StateType

  protected def newState(implicit pc: PipelineContext, ec: ExternalContext): StateType

  protected def getKeyValidity(state: StateType)(implicit pc: PipelineContext, ec: ExternalContext): Option[(OutputCacheKey, ValidityType)]

  protected def read(state: StateType)(implicit pc: PipelineContext, ec: ExternalContext, rcv: XMLReceiver): Unit

  private class StateWrapper(val state: StateType) {
    var keyValidity: Map[String, (OutputCacheKey, ValidityType)] = Map.empty
  }

  private def getOrSetState(implicit pc: PipelineContext, ec: ExternalContext): StateWrapper =
    processor
      .hasState(pc)
      .option(processor.getState(pc).asInstanceOf[StateWrapper])
      .getOrElse {
        new StateWrapper(newState)
          .tap(processor.setState(pc, _))
      }

  final override protected def getKeyImpl(pipelineContext: PipelineContext): OutputCacheKey = {
    val ec = NetUtils.getExternalContext
    val state = getOrSetState(pipelineContext, ec)
    state.keyValidity.get(getName) match {
      case Some((key, _)) =>
        key
      case None =>
        getKeyValidity(state.state)(pipelineContext, ec) match {
          case Some(kv @ (key, _)) =>
            state.keyValidity += getName -> kv
            key
          case None =>
            null
        }
    }
  }

  final override protected def getValidityImpl(pipelineContext: PipelineContext): AnyRef = {
    val ec = NetUtils.getExternalContext
    val state = getOrSetState(pipelineContext, ec)
    state.keyValidity.get(getName) match {
      case Some((_, validity)) =>
        validity.asInstanceOf[AnyRef]
      case None =>
        getKeyValidity(state.state)(pipelineContext, ec) match {
          case Some(kv @ (_, validity)) =>
            state.keyValidity += getName -> kv
            validity.asInstanceOf[AnyRef]
          case None =>
            null
        }
    }
  }

  final override protected def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {
    val ec = NetUtils.getExternalContext
    read(getOrSetState(pipelineContext, ec).state)(pipelineContext, ec, xmlReceiver)
  }
}