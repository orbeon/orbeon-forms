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

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsContainingDocument;

/**
 * Represent the lifecycle of an XForms document from the point of view of state handling.
 */
public interface XFormsStateLifecycle {
    String getClientEncodedStaticState(PropertyContext propertyContext, XFormsContainingDocument containingDocument);
    String getClientEncodedDynamicState(PropertyContext propertyContext, XFormsContainingDocument containingDocument);
    void afterInitialResponse(PropertyContext propertyContext, XFormsContainingDocument containingDocument);

    XFormsContainingDocument findOrRestoreDocument(PipelineContext pipelineContext, String uuid, String encodedClientStaticState,
                                                   String encodedClientDynamicState, boolean isInitialState);
    void beforeUpdateResponse(PropertyContext propertyContext, XFormsContainingDocument containingDocument, boolean ignoreSequence);
    void afterUpdateResponse(PropertyContext propertyContext, XFormsContainingDocument containingDocument);
    void onUpdateError(PropertyContext propertyContext, XFormsContainingDocument containingDocument);

    void onAdd(PropertyContext propertyContext, XFormsContainingDocument containingDocument);
    void onRemove(PropertyContext propertyContext, XFormsContainingDocument containingDocument);
    void onEvict(PropertyContext propertyContext, XFormsContainingDocument containingDocument);
}
