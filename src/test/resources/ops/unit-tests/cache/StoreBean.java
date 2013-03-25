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

import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.SimpleProcessor;
import org.orbeon.oxf.xml.XPathUtils;

public class StoreBean extends SimpleProcessor {

    public StoreBean() {
    }

    public void start(PipelineContext pipelineContext) {

        final Document config = readCacheInputAsDOM4J(pipelineContext, "bean-config");

        final String keyName = XPathUtils.selectStringValue(config, "/*/key");
        final String houseName = XPathUtils.selectStringValue(config, "/*/house-name");

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        if (externalContext == null)
            throw new OXFException("Missing external context");

        externalContext.getRequest().getAttributesMap().put(keyName, new org.orbeon.oxf.test.beans.House(houseName));
    }
}
