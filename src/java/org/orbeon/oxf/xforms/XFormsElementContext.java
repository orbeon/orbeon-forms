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
package org.orbeon.oxf.xforms;

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.xforms.output.element.XFormsElement;
import org.xml.sax.ContentHandler;

import java.util.Stack;

/**
 * Context in which control elements are executed.
 */
public class XFormsElementContext extends XFormsControls {

    private PipelineContext pipelineContext;
    private ContentHandler contentHandler;

    private Stack elements = new Stack();

    private String encryptionPassword;

    public XFormsElementContext(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, ContentHandler contentHandler) {

        super(containingDocument, null);
        super.initialize();

        this.pipelineContext = pipelineContext;
        this.contentHandler = contentHandler;
        this.encryptionPassword = XFormsUtils.getEncryptionKey();
    }

    public ContentHandler getContentHandler() {
        return contentHandler;
    }

    public void pushElement(XFormsElement element) {
        elements.push(element);
    }

    public XFormsElement popElement() {
        return (XFormsElement) elements.pop();
    }

    public XFormsElement peekElement() {
        return (XFormsElement) elements.peek();
    }

    public XFormsElement getParentElement(int level) {
        return elements.size() > level + 1 ? (XFormsElement) elements.get(elements.size() - (level + 2)) : null;
    }

    public PipelineContext getPipelineContext() {
        return pipelineContext;
    }

    public String getEncryptionPassword() {
        return encryptionPassword;
    }
}
