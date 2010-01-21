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
package org.orbeon.oxf.xforms;

import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.saxon.om.DocumentInfo;

/**
 * XFormsInstance that can be shared among multiple users. It must be passed a DocumentInfo and it is not possible to
 * replace the instance document.
 */
public class ReadonlyXFormsInstance extends XFormsInstance {

    public ReadonlyXFormsInstance(Element containerElement) {
        super(containerElement);
    }

    public ReadonlyXFormsInstance(String modelId, String instanceStaticId, DocumentInfo instanceDocumentInfo,
                                  String instanceSourceURI, String requestBodyHash,
                                  String username, String password, boolean cache, long timeToLive, String validation,
                                  boolean handleXInclude, boolean exposeXPathTypes) {
        super(modelId, instanceStaticId, instanceDocumentInfo, instanceSourceURI, requestBodyHash, username, password, cache, timeToLive, validation, handleXInclude, exposeXPathTypes);
    }

    /**
     * Create a mutable version of this instance with the same instance document.
     *
     * @return  mutable XFormsInstance
     */
    public XFormsInstance createMutableInstance() {
        final Document mutableDocument = TransformerUtils.tinyTreeToDom4j2(getDocumentInfo());
        return new XFormsInstance(modelEffectiveId, instanceStaticId, mutableDocument, getSourceURI(), getRequestBodyHash(), getUsername(), getPassword(),
                isCache(), getTimeToLive(), getValidation(), isHandleXInclude(), isExposeXPathTypes());
    }
}
