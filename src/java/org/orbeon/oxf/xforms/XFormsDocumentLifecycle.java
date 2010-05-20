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

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.event.XFormsEvent;

/**
 * Represent the lifecycle of an XForms document from the point of view of an Ajax request.
 */
public interface XFormsDocumentLifecycle {
    void beforeExternalEvents(PipelineContext pipelineContext, ExternalContext.Response response, boolean handleGoingOnline);
    void handleExternalEvent(PipelineContext pipelineContext, XFormsEvent event, boolean handleGoingOnline);
    void afterExternalEvents(PipelineContext pipelineContext, boolean handleGoingOnline);
}
