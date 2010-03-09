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
package org.orbeon.oxf.externalcontext;

import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.processor.test.TestExternalContext;
import org.orbeon.oxf.test.ResourceManagerTestBase;
import org.orbeon.oxf.util.NetUtils;

import java.io.UnsupportedEncodingException;
import java.util.Map;

public class ForwardExternalContextRequestWrapperTest extends ResourceManagerTestBase {

    private ExternalContext.Request request;

    protected void setUp() throws Exception {

        final PipelineContext pipelineContext = new PipelineContext();

        final Document requestDocument = ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/test/if-modified-since-request.xml", null);
        final ExternalContext externalContext = new TestExternalContext(pipelineContext, requestDocument);
        pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext);
        request = externalContext.getRequest();
    }

    public void testGetParameterMap() {

        final byte[] messageBody;
        try {
            messageBody = "name1=value1b&name1=value1c&name2=value2b".getBytes("utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e);
        }

        final ForwardExternalContextRequestWrapper wrapper = new ForwardExternalContextRequestWrapper(request,
                "/orbeon", "/foobar?name1=value1a&name2=value2a&name3=value3", "post", "application/x-www-form-urlencoded",
                messageBody, null, null);

        final Map<String, Object[]> parameters = wrapper.getParameterMap();

        assertEquals("name1=value1a&name1=value1b&name1=value1c&name2=value2a&name2=value2b&name3=value3", NetUtils.encodeQueryString(parameters));
    }
}
