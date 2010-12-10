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
package org.orbeon.oxf.xforms.event.events;

import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.event.*;

import java.util.Map;


/**
 * Internal xxforms-submit event.
 */
public class XXFormsProcessUploadEvent extends XFormsEvent {

    private final String file;
    private final String mediatype;
    private final String size;
    private final String filename;

    public XXFormsProcessUploadEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject, Map<String, String> parameters) {
        super(containingDocument, XFormsEvents.XXFORMS_PROCESS_UPLOAD, targetObject, false, false);

        this.file = parameters.get("file");
        this.filename = parameters.get("filename");
        this.mediatype = parameters.get("content-type");
        this.size = parameters.get("content-length");
    }

    public String getFile() {
        return file;
    }

    public String getMediatype() {
        return mediatype;
    }

    public String getSize() {
        return size;
    }

    public String getFilename() {
        return filename;
    }
}
