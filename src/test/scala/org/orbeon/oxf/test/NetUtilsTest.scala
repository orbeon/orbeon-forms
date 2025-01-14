package org.orbeon.oxf.test

import org.junit.Assert.{assertFalse, assertTrue}
import org.junit.{After, Before, Test}
import org.orbeon.oxf.externalcontext.{CachingResponseSupport, RequestAdapter, TestExternalContext}
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorUtils
import org.orbeon.oxf.util.DateUtils

import scala.jdk.CollectionConverters.*


class NetUtilsTest extends ResourceManagerTestBase {

  private var pipelineContext: PipelineContext = null

  @Before override def setupResourceManagerTestPipelineContext(): Unit = {
    pipelineContext = new PipelineContext
    val requestDocument = ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/test/if-modified-since-request.xml", null)
    val externalContext = new TestExternalContext(pipelineContext, requestDocument)
    pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext)
  }

  @After override def tearDownResourceManagerTestPipelineContext(): Unit = {
    pipelineContext.destroy(true)
  }

  @Test def testCheckIfModifiedSince(): Unit = {

    val ifModifiedHeaderString = "Thu, 28 Jun 2007 14:17:36 GMT"
    val ifModifiedHeaderLong   = DateUtils.parseRFC1123(ifModifiedHeaderString)

    val request = new RequestAdapter {
      override def getMethod: HttpMethod = HttpMethod.GET
      override val getHeaderValuesMap = Map("if-modified-since" -> Array(ifModifiedHeaderString)).asJava
    }

    assertFalse(CachingResponseSupport.checkIfModifiedSince(request, ifModifiedHeaderLong - 1)(null))
    assertFalse(CachingResponseSupport.checkIfModifiedSince(request, ifModifiedHeaderLong)(null))
    // For some reason the code checks that there is more than one second of difference
    assertTrue(CachingResponseSupport.checkIfModifiedSince(request, ifModifiedHeaderLong + 1001)(null))
  }
}
