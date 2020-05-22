/**
  * Copyright (C) 2010 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  * 2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.processor.scope

import enumeratum._
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{CacheableInputReader, ProcessorImpl, ProcessorInput, ProcessorUtils}

object ScopeProcessorBase {

  sealed trait Scope extends EnumEntry
  object Scope extends Enum[Scope] {

    val values = findValues

    case object Request     extends Scope
    case object Session     extends Scope
    case object Application extends Scope
  }

  val TextPlain               = "text/plain"
  val ScopeConfigNamespaceUri = "http://orbeon.org/oxf/schemas/scope-config"

  case class ContextConfig(
    contextType                 : Scope,
    sessionScope                : ExternalContext.SessionScope,
    key                         : String,
    isTextPlain                 : Boolean,
    testIgnoreStoredKeyValidity : Boolean
  ) {
    def javaIsRequestScope     = contextType == Scope.Request
    def javaIsSessionScope     = contextType == Scope.Session
    def javaIsApplicationScope = contextType == Scope.Application
  }
}

abstract class ScopeProcessorBase extends ProcessorImpl {

  import ScopeProcessorBase._

  protected def readConfig(context: PipelineContext): ContextConfig =
    readCacheInputAsObject(
      context,
      getInputByName(ProcessorImpl.INPUT_CONFIG),
      new CacheableInputReader[ContextConfig]() {
        override def read(context: PipelineContext, input: ProcessorInput): ContextConfig = {

          val rootElement = readInputAsOrbeonDom(context, input).getRootElement

          val contextName           = rootElement.element("scope").getStringValue
          val sessionScopeElement   = rootElement.element("session-scope")
          val contentTypeElementOpt = Option(rootElement.element(Headers.ContentTypeLower))

          val sessionScopeValue =
            if (sessionScopeElement == null)
              null
            else
              sessionScopeElement.getStringValue

          ContextConfig(
            Scope.withNameLowercaseOnly(contextName),
            if ("application" == sessionScopeValue)
              ExternalContext.SessionScope.Application
            else
              ExternalContext.SessionScope.Local,
            rootElement.element("key").getStringValue,
            contentTypeElementOpt map (_.getStringValue) contains TextPlain,
            ProcessorUtils.selectBooleanValue(rootElement, "/*/test-ignore-stored-key-validity", false)
          )
        }
      }
    )
}