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
package org.orbeon.oxf.util

import org.orbeon.oxf.http.Headers._
import org.orbeon.oxf.http.StreamedContent

import collection.JavaConverters._
import org.junit.Test
import org.mockito.Mockito
import org.orbeon.oxf.externalcontext.LocalRequest
import org.orbeon.oxf.externalcontext.RequestAdapter
import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.orbeon.oxf.webapp.WebAppContext
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import collection.mutable

class ConnectionTest extends ResourceManagerTestBase with AssertionsForJUnit with MockitoSugar {
    
    @Test def forwardHeaders(): Unit = {
        
        // Custom headers
        val customHeaderValuesMap = Map(
            "my-stuff"   → List("my-value"),
            "your-stuff" → List("your-value-1", "your-value-2")
        )
        
        // Create request and wrapper
        val incomingRequest = new RequestAdapter {
            override val getHeaderValuesMap = mutable.LinkedHashMap(
                "user-agent"    → Array("Mozilla 12.1"),
                "authorization" → Array("xsifj1skf3"),
                "host"          → Array("localhost"),
                "cookie"        → Array("JSESSIONID=4FF78C3BD70905FAB502BC989450E40C")
            ).asJava
        }

        val webAppContext = Mockito.mock(classOf[WebAppContext])
        Mockito when webAppContext.attributes thenReturn collection.mutable.Map[String, AnyRef]()

        val externalContext = Mockito.mock(classOf[ExternalContext])
        Mockito when externalContext.getRequest thenReturn incomingRequest
        Mockito when externalContext.getWebAppContext thenReturn webAppContext
        
        // NOTE: Should instead use withExternalContext()
        PipelineContext.get.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext)
        val headers =
            Connection.buildConnectionHeadersLowerWithSOAPIfNeeded(
                scheme            = "http",
                httpMethodUpper   = "GET",
                credentialsOrNull = null,
                mediatype         = null,
                encodingForSOAP   = "UTF-8",
                customHeaders     = customHeaderValuesMap,
                headersToForward  = "cookie authorization user-agent")(
                logger            = ResourceManagerTestBase.newIndentedLogger
            )

        val request =
            new LocalRequest(
                incomingRequest         = externalContext.getRequest,
                contextPath             = "/orbeon",
                pathQuery               = "/foo/bar",
                methodUpper             = "GET",
                headersMaybeCapitalized = headers,
                content                 = None
            )

        // Test standard headers received
        val headerValuesMap = request.getHeaderValuesMap.asScala

        assert("Mozilla 12.1"                                === headerValuesMap("user-agent")(0))
        assert("xsifj1skf3"                                  === headerValuesMap("authorization")(0))
        assert("JSESSIONID=4FF78C3BD70905FAB502BC989450E40C" === headerValuesMap("cookie")(0))
        assert(None                                          === headerValuesMap.get("host"))
        assert(None                                          === headerValuesMap.get("foobar"))

        // Test custom headers received
        assert("my-value"                                    === headerValuesMap("my-stuff")(0))
        assert("your-value-1"                                === headerValuesMap("your-stuff")(0))
        assert("your-value-2"                                === headerValuesMap("your-stuff")(1))
    }

    @Test def combinedParameters(): Unit = {

        val queryString = "name1=value1a&name2=value2a&name3=value3"
        val messageBody = "name1=value1b&name1=value1c&name2=value2b".getBytes("utf-8")

        // POST configuration
        val method = "POST"
        val bodyMediaType = "application/x-www-form-urlencoded"
        val explicitHeaders = Map(ContentTypeLower → List(bodyMediaType))

        val headers =
            Connection.buildConnectionHeadersLowerWithSOAPIfNeeded(
                scheme            = "http",
                httpMethodUpper   = method,
                credentialsOrNull = null,
                mediatype         = bodyMediaType,
                encodingForSOAP   = "UTF-8",
                customHeaders     = explicitHeaders,
                headersToForward  = "")(
                logger            = ResourceManagerTestBase.newIndentedLogger
            )

        val wrapper =
            new LocalRequest(
                incomingRequest         = NetUtils.getExternalContext.getRequest,
                contextPath             = "/orbeon",
                pathQuery               = s"/foobar?$queryString",
                methodUpper             = method,
                headersMaybeCapitalized = headers,
                content                 = Some(StreamedContent.fromBytes(messageBody, Some(bodyMediaType)))
            )

        val parameters = wrapper.getParameterMap
        assert("name1=value1a&name1=value1b&name1=value1c&name2=value2a&name2=value2b&name3=value3" === NetUtils.encodeQueryString(parameters))
    }
}