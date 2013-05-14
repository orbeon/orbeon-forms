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
package org.orbeon.oxf.processor.xforms.input.action;

import org.jaxen.FunctionContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.saxon.om.DocumentInfo;

import java.util.Map;

public interface Action {

    String NODESET_ATTRIBUTE_NAME = "node-ids";
    String AT_ATTRIBUTE_NAME = "at";
    String POSITION_ATTRIBUTE_NAME = "position";
    String VALUE_ATTRIBUTE_NAME = "value";
    String CONTENT_ATTRIBUTE_NAME = "content";

    void setParameters(Map parameters);
    void run(PipelineContext context, FunctionContext functionContext, String encryptionPassword, DocumentInfo instance);
}
