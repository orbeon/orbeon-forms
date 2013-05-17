/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.SimpleProcessor;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.XPathUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;
import org.dom4j.Document;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class StoreBean extends SimpleProcessor {

    public StoreBean() {
    }

    public void start(PipelineContext pipelineContext) {

        Document config = readCacheInputAsDOM4J(pipelineContext, "bean-config");

        String keyName = XPathUtils.selectStringValue(config, "/*/key");
        String houseName = XPathUtils.selectStringValue(config, "/*/house-name");

        ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        if (externalContext == null)
            throw new OXFException("Missing external context");

        externalContext.getRequest().getAttributesMap().put(keyName, new org.orbeon.oxf.test.beans.House(houseName));
    }
}
