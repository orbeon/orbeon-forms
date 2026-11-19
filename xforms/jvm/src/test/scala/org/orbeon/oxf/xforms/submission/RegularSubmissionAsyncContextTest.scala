/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.submission

import cats.effect.IO
import org.mockito.{ArgumentMatchers, Mockito}
import org.orbeon.io.IOUtils
import org.orbeon.oxf.externalcontext.{ExternalContext, LocalResponse, ServletURLRewriter, TestExternalContext}
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.test.{DocumentTestBase, PipelineSupport, ResourceManagerSupport}
import org.orbeon.oxf.util.{ContentTypes, CoreCrossPlatformSupport}
import org.orbeon.oxf.webapp.ProcessorService
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.analytics.{DocumentRemovalReason, SubmissionCategory, XFormsAnalyticsListener, XFormsAnalyticsSupport}
import org.orbeon.oxf.xforms.event.events.XFormsSubmitEvent
import org.orbeon.oxf.xforms.event.{Dispatch, EventCollector}
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*


class RegularSubmissionAsyncContextTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("RegularSubmission async and deferred internal connections") {

    it("must propagate ProcessorService to Cats Effect worker threads for deferred replace=\"all\" and xxf:async submissions") {

      // Warm up Cats Effect worker threads while `ProcessorService.currentProcessorService` is empty so worker
      // threads do not accidentally inherit a `ProcessorService` via `InheritableThreadLocal`.
      assert(ProcessorService.currentProcessorService.value.isEmpty)
      IO.cede.replicateA_(32).unsafeRunSync()(CoreCrossPlatformSupport.runtime)

      val recordedCalls = new ConcurrentLinkedQueue[(String, SubmissionCategory, Boolean)]()
      val prevListener  = XFormsAnalyticsSupport.listenerOpt

      val testListener = new XFormsAnalyticsListener {
        def onHttpSessionCreated(sessionId: String, createdAtMs: Long, maxInactiveSec: Int): Unit = ()
        def onHttpSessionDestroyed(sessionId: String, destroyedAtMs: Long, documentUuids: List[String]): Unit = ()
        def onDocumentInitialized(doc: XFormsContainingDocument, sessionIdOpt: Option[String], usernameOpt: Option[String], maxInactiveSec: Int, durationMs: Long): Unit = ()
        def onDocumentInitFailed(requestPath: String, durationMs: Long): Unit = ()
        def onAjaxUpdateCompleted(doc: XFormsContainingDocument, sessionIdOpt: Option[String], usernameOpt: Option[String], maxInactiveSec: Int, isInteraction: Boolean, durationMs: Long, hadServerError: Boolean): Unit = ()
        def onDocumentPassivated(uuid: String, atMs: Long): Unit = ()
        def onDocumentActivatedFromStore(uuid: String, atMs: Long): Unit = ()
        def onDocumentRemoved(uuid: String, reason: DocumentRemovalReason, atMs: Long): Unit = ()
        def onSubmissionCompleted(
          docOpt     : Option[XFormsContainingDocument],
          targetUri  : String,
          category   : SubmissionCategory,
          durationMs : Long,
          succeeded  : Boolean
        ): Unit =
          recordedCalls.add((targetUri, category, succeeded))
      }

      val mockProcessorService = Mockito.mock(classOf[ProcessorService])
      Mockito.doAnswer { invocation =>
        val ec  = invocation.getArgument[ExternalContext](1)
        val req = ec.getRequest
        val res = ec.getResponse

        assert(ProcessorService.currentProcessorService.value.contains(mockProcessorService))

        req.getRequestPath match {
          case "/fr/orbeon/bookshelf/view/123" =>
            res.setStatus(StatusCode.Ok)
            res.setContentType(ContentTypes.HtmlContentType)
            res.getWriter.write("<html><body>View Mode</body></html>")
            res.getWriter.flush()
          case "/fr/service/custom/echo" =>
            res.setStatus(StatusCode.Ok)
            res.setContentType(ContentTypes.XmlContentType)
            res.getWriter.write("<response>async-ok</response>")
            res.getWriter.flush()
          case other =>
            fail(s"Unexpected internal request path: $other")
        }
        null
      }.when(mockProcessorService).service(ArgumentMatchers.any(), ArgumentMatchers.any())

      val formDoc =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
          <xh:head>
            <xf:model id="main-model">
              <xf:instance id="data">
                <form><title>Book</title></form>
              </xf:instance>
              <xf:instance id="async-res">
                <response/>
              </xf:instance>
              <xf:submission
                id="sub-async"
                method="post"
                mode="asynchronous"
                xxf:response-must-await="forever"
                resource="/fr/service/custom/echo"
                ref="instance('data')"
                replace="instance"
                instance="async-res"/>
              <xf:submission
                id="sub-deferred"
                method="post"
                resource="/fr/orbeon/bookshelf/view/123"
                ref="instance('data')"
                replace="all"/>
            </xf:model>
          </xh:head>
          <xh:body/>
        </xh:html>.toDocument

      XFormsAnalyticsSupport.listenerOpt = Some(testListener)
      try {
        ProcessorService.withProcessorService(mockProcessorService) {
          PipelineSupport.withPipelineContextAndTestExternalContext(
            XFormsStateManager.sessionCreated,
            XFormsStateManager.sessionDestroyed
          ) { (_, ec) =>
            val xfcd = setupDocument(formDoc, setupBeforeExternalEvents = false)
            ec.asInstanceOf[TestExternalContext].requestDocument.getRootElement.element("method").setText("POST")
            try {
              // 1. Asynchronous internal submission (`mode="asynchronous"`)
              XFormsAPI.withContainingDocument(xfcd) {
                xfcd.withExternalEvents(ec.getResponse, submissionIdOpt = None) {
                  val subAsync = xfcd.getObjectByEffectiveId("sub-async").asInstanceOf[XFormsModelSubmission]
                  Dispatch.dispatchEvent(new XFormsSubmitEvent(subAsync), EventCollector.Throw)
                }
              }
              assert(xfcd.findInstance("async-res").get.rootElement.getStringValue == "async-ok")

              // 2. Two-pass deferred `replace="all"` internal submission (same path as edit -> view navigation)
              val pass1Response = new LocalResponse(new ServletURLRewriter(ec.getRequest))
              XFormsAPI.withContainingDocument(xfcd) {
                xfcd.withExternalEvents(pass1Response, submissionIdOpt = None) {
                  val subDeferred = xfcd.getObjectByEffectiveId("sub-deferred").asInstanceOf[XFormsModelSubmission]
                  Dispatch.dispatchEvent(new XFormsSubmitEvent(subDeferred), EventCollector.Throw)
                }
              }

              val twoPassSubmissionId =
                xfcd.findTwoPassSubmitEvents.headOption
                  .flatMap(_.submissionId)
                  .getOrElse(fail("Expected two-pass submit event to be queued on first pass"))

              val pass2Response = new LocalResponse(new ServletURLRewriter(ec.getRequest))
              XFormsAPI.withContainingDocument(xfcd) {
                xfcd.withExternalEvents(pass2Response, submissionIdOpt = Some(twoPassSubmissionId))(())
              }

              val replaceAllIo = xfcd.getReplaceAllFuture.getOrElse(fail("Expected `replaceAllFuture` to be set on second pass"))
              XFormsModelSubmissionSupport.runDeferredSubmissionForUpdate(replaceAllIo, pass2Response)

              val out = new ByteArrayOutputStream
              IOUtils.copyStreamAndClose(pass2Response.getInputStream, out)
              val bodyStr = new String(out.toByteArray, StandardCharsets.UTF_8)
              assert(pass2Response.getStatus == StatusCode.Ok)
              assert(bodyStr.contains("View Mode"))

              val recorded = recordedCalls.asScala.toList
              assert(recorded.exists { case (uri, cat, ok) => uri.endsWith("/fr/service/custom/echo") && cat == SubmissionCategory.Service && ok })
              assert(recorded.exists { case (uri, cat, ok) => uri.endsWith("/fr/orbeon/bookshelf/view/123") && cat == SubmissionCategory.Service && ok })
            } finally {
              disposeDocument()
            }
          }
        }
      } finally {
        XFormsAnalyticsSupport.listenerOpt = prevListener
      }
    }
  }
}
