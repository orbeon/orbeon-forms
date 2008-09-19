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
package org.orbeon.oxf.processor;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.DOMWriter;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xml.XMLUtils;

/**
 * Serializes the data input into a DOM.
 */
public class DOMSerializer extends ProcessorImpl {

    public DOMSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
    }

    /**
     * Find the last modified timestamp of the dependencies of this processor.
     *
     * @param pipelineContext       pipeline context
     * @return                      timestamp, <= 0 if unknown
     */
    public long findInputLastModified(PipelineContext pipelineContext) {
        return findInputLastModified(pipelineContext, getInputByName(INPUT_DATA), false);
    }

    public Document getDocument(PipelineContext pipelineContext) {
        return (Document) pipelineContext.getAttribute(this);
    }

    public org.w3c.dom.Document getW3CDocument(PipelineContext pipelineContext) {
        DOMWriter writer = new DOMWriter(XMLUtils.createDocument().getClass());
        try {
            return writer.write(getDocument(pipelineContext));
        } catch (DocumentException e) {
            throw new OXFException(e);
        }
    }

    public void start(PipelineContext pipelineContext) {
        // FIXME: should use Context instead?
        pipelineContext.setAttribute(this, readCacheInputAsDOM4J(pipelineContext, INPUT_DATA));
    }
}
