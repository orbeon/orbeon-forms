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
package org.orbeon.oxf.xforms.state;

import org.dom4j.Document;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsContainingDocument;

/**
 * Represent the lifecycle of an XForms document from the point of view of state handling.
 */
public interface XFormsStateLifecycle {

    interface RequestParameters {
        String getUUID();
        String getEncodedClientStaticState();
        String getEncodedClientDynamicState();
    }

    String getClientEncodedStaticState(XFormsContainingDocument containingDocument);
    String getClientEncodedDynamicState(XFormsContainingDocument containingDocument);
    void afterInitialResponse(XFormsContainingDocument containingDocument);

    RequestParameters extractParameters(Document request, boolean isInitialState);

    XFormsContainingDocument findOrRestoreDocument(RequestParameters parameters, boolean isInitialState);

    XFormsContainingDocument beforeUpdate(RequestParameters parameters);
    void beforeUpdateResponse(XFormsContainingDocument containingDocument, boolean ignoreSequence);
    void afterUpdateResponse(XFormsContainingDocument containingDocument);
    void afterUpdate(XFormsContainingDocument containingDocument, boolean keepDocument);

    void onAddedToCache(String uuid);
    void onRemovedFromCache(String uuid);
    void onEvictedFromCache(XFormsContainingDocument containingDocument);
}
