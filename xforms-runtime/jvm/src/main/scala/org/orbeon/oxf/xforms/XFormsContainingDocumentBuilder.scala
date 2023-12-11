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
package org.orbeon.oxf.xforms

import cats.syntax.option._
import org.orbeon.oxf.common.OrbeonLocationException
import org.orbeon.oxf.controller.PageFlowControllerProcessor
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.logging.LifecycleLogger
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, PathMatcher, StringConversions}
import org.orbeon.oxf.xforms.XFormsProperties.NoUpdates
import org.orbeon.oxf.xforms.analysis.{Metadata, StaticStateDocument}
import org.orbeon.oxf.xforms.processor.XFormsURIResolver
import org.orbeon.oxf.xforms.state.{AnnotatedTemplateBuilder, XFormsState, XFormsStaticStateCache}
import org.orbeon.oxf.xml.EncodeDecode
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xforms.{DeploymentType, XFormsCrossPlatformSupport}

import java.{util => ju}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal


object XFormsContainingDocumentBuilder {

  /**
   * Create an `XFormsContainingDocument` from an `XFormsStaticState` object.
   *
   * Used by `XFormsToXHTML` and tests.
   *
   * @param staticState     static state object
   * @param uriResolver     for loading instances during initialization (and possibly more, such as schemas and `GET` submissions upon initialization)
   * @param response        optional response for handling `replace="all"` during initialization
   * @param mustInitialize  initialize document (`false` for testing only)
   */
  def apply(
    staticState    : XFormsStaticState,
    uriResolver    : Option[XFormsURIResolver],
    response       : Option[ExternalContext.Response],
    mustInitialize : Boolean
  ): XFormsContainingDocument =
    try {

      implicit val ec: ExternalContext = CoreCrossPlatformSupport.externalContext

      val uuid = CoreCrossPlatformSupport.randomHexId

      // attempt to ignore `oxf:xforms-submission`
      if (staticState.propertyMaybeAsExpression(NoUpdates).fold(_.toString != "true" , _ => true))
        LifecycleLogger.eventAssumingRequest("xforms", "new form session", List("uuid" -> uuid))

      val doc = new XFormsContainingDocument(staticState, uuid, disableUpdates = false)
      implicit val logger = doc.indentedLogger
      withDebug("initialization: creating new ContainingDocument (static state object provided).", List("uuid" -> uuid)) {

        doc.setRequestInformation(createInitialRequestInformation)

        if (mustInitialize)
          doc.initialize(uriResolver, response)
      }
      doc
    } catch {
      case NonFatal(t) =>
        throw OrbeonLocationException.wrapException(t, XmlExtendedLocationData(null, "initializing XForms containing document".some))
    }

  private def createInitialRequestInformation: RequestInformation =
    Option(XFormsCrossPlatformSupport.externalContext.getRequest) match {
      case Some(request) =>
        // Remember if filter provided separate deployment information

        import org.orbeon.oxf.servlet.OrbeonXFormsFilterImpl._

        val deploymentType =
          request.getAttributesMap.get(RendererDeploymentAttributeName).asInstanceOf[String] match {
            case "separate"   => DeploymentType.Separate
            case "integrated" => DeploymentType.Integrated
            case _            => DeploymentType.Standalone
          }

        val containerType = request.getContainerType

        val requestHeaders =
          request.getHeaderValuesMap.asScala mapValues (_.toList) toMap

        val versionedPathMatchers =
          Option(PipelineContext.get.getAttribute(PageFlowControllerProcessor.PathMatchers).asInstanceOf[ju.List[PathMatcher]]) getOrElse
            ju.Collections.emptyList[PathMatcher]

        RequestInformation(
          deploymentType        = deploymentType,
          requestMethod         = request.getMethod,
          requestContextPath    = request.getClientContextPath("/"),
          requestPath           = Option(request.getAttributesMap.get(RendererBaseUriAttributeName).asInstanceOf[String]) getOrElse request.getRequestPath,
          requestHeaders        = requestHeaders,
          requestParameters     = request.parameters mapValues StringConversions.objectArrayToStringArray mapValues (_.toList) toMap,
          containerType         = containerType,
          containerNamespace    = request.getContainerNamespace.trimAllToOpt.getOrElse(""),
          versionedPathMatchers = versionedPathMatchers.asScala.toList,
          embeddingType         = Headers.embeddedClientValueFromHeaders(requestHeaders),
          forceInlineResources  = isPortletContainerOrRemoteFromHeaders(containerType, requestHeaders)
        )

      case None =>
        // Special case when we run outside the context of a request
        RequestInformation(
          deploymentType        = DeploymentType.Standalone,
          requestMethod         = HttpMethod.GET,
          requestContextPath    = "",
          requestPath           = "/",
          requestHeaders        = Map.empty,
          requestParameters     = Map.empty,
          containerType         = "servlet",
          containerNamespace    = "",
          versionedPathMatchers = Nil,
          embeddingType         = None,
          forceInlineResources  = false
        )
    }

  /**
   * Restore an `XFormsContainingDocument` from `XFormsState` only.
   *
   * Used by `XFormsStateManager`.
   *
   * @param xformsState    XFormsState containing static and dynamic state
   * @param disableUpdates whether to disable updates (for recreating initial document upon browser back)
   */
  def apply(
    xformsState     : XFormsState,
    disableUpdates  : Boolean,
    forceEncryption : Boolean)(
    indentedLogger  : IndentedLogger
  ): XFormsContainingDocument =
    try {
      // 1. Restore the static state
      val staticState = findOrRestoreStaticState(xformsState, forceEncryption)(indentedLogger)

      // 2. Restore the dynamic state
      val dynamicState = xformsState.dynamicState getOrElse (throw new IllegalStateException)

      val doc = new XFormsContainingDocument(staticState, dynamicState.uuid, disableUpdates)
      implicit val logger = doc.indentedLogger
      withDebug("initialization: restoring containing document") {

        val requestHeaders     = dynamicState.requestHeaders.toMap
        val containerType      = dynamicState.containerType.orNull

        val deploymentTypeOpt =
          dynamicState.deploymentType map DeploymentType.withNameInsensitive

        // Normal case where information below was previously serialized
        doc.restoreDynamicState(
          dynamicState.sequence,
          dynamicState.decodeDelayedEvents,
          dynamicState.decodePendingUploads,
          dynamicState.decodeLastAjaxResponse,
          dynamicState.decodeInstancesControls,
          dynamicState.focusedControl,
          deploymentTypeOpt map (deploymentType =>
            RequestInformation(
              deploymentType        = deploymentType,
              requestMethod         = dynamicState.requestMethod.orNull,
              requestContextPath    = dynamicState.requestContextPath.orNull,
              requestPath           = dynamicState.requestPath.orNull,
              requestHeaders        = requestHeaders,
              requestParameters     = dynamicState.requestParameters.toMap,
              containerType         = containerType,
              containerNamespace    = dynamicState.containerNamespace.orNull,
              versionedPathMatchers = dynamicState.decodePathMatchers,
              embeddingType         = Headers.embeddedClientValueFromHeaders(requestHeaders),
              forceInlineResources  = isPortletContainerOrRemoteFromHeaders(containerType, requestHeaders)
            )
          ) getOrElse
            createInitialRequestInformation
        )
      }
      doc
    } catch {
      case NonFatal(t) =>
        throw OrbeonLocationException.wrapException(t, XmlExtendedLocationData(null, "re-initializing XForms containing document".some))
    }

  private def isPortletContainerOrRemoteFromHeaders(containerType: String, headers: Map[String, List[String]]) =
    containerType == "portlet" || (Headers.firstItemIgnoreCase(headers, Headers.OrbeonClient) contains Headers.PortletEmbeddingClient)

  // Create static state from an encoded version. This is used when restoring a static state from a serialized form.
  // NOTE: `digest` can be None when using client state, if all we have are serialized static and dynamic states.
  def restoreStaticState(digest: Option[String], encodedState: String, forceEncryption: Boolean): XFormsStaticStateImpl = {

    val staticStateDocument = new StaticStateDocument(EncodeDecode.decodeXML(encodedState, forceEncryption))

    // Restore template
    val template = staticStateDocument.template map AnnotatedTemplateBuilder.apply

    // Restore metadata
    val metadata = Metadata(staticStateDocument, template)

    XFormsStaticStateImpl(
      encodedState,
      staticStateDocument.getOrComputeDigest(digest),
      new Scope(None, ""),
      metadata,
      template,
      staticStateDocument
    )
  }

  private def findOrRestoreStaticState(
    xformsState     : XFormsState,
    forceEncryption : Boolean)(implicit
    indentedLogger  : IndentedLogger
  ): XFormsStaticState =
    xformsState.staticStateDigest match {
      case digestOpt @ Some(digest) =>
        (XFormsStaticStateCache.findDocument(digest) match {
          case Some((cachedState, _)) =>
            // Found static state in cache
            debug("found static state by digest in cache")
            cachedState
          case _ =>
            // Not found static state in cache, create static state from input
            debug("did not find static state by digest in cache")
            val restoredStaticState =
              withDebug("initialization: restoring static state") {
                restoreStaticState(
                  digest          = digestOpt,
                  encodedState    = xformsState.staticState getOrElse (throw new IllegalStateException),
                  forceEncryption = forceEncryption
                )
              }
            // Store in cache
            XFormsStaticStateCache.storeDocument(restoredStaticState)
            restoredStaticState
        }) ensuring (_.isServerStateHandling)
      case digestOpt @ None =>
        // Not digest provided, create static state from input
        debug("did not find static state by digest in cache")
        restoreStaticState(
          digest          = digestOpt,
          encodedState    = xformsState.staticState getOrElse (throw new IllegalStateException),
          forceEncryption = forceEncryption
        ) ensuring (_.isClientStateHandling)
    }
}