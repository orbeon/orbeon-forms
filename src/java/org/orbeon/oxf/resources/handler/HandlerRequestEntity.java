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
package org.orbeon.oxf.resources.handler;

import org.apache.commons.httpclient.methods.RequestEntity;

import java.io.IOException;
import java.io.OutputStream;

public class HandlerRequestEntity implements RequestEntity {

    private byte[] requestBody;
    private  String contentType;

    public HandlerRequestEntity(byte[] requestBody, String contentType) {
        this.requestBody = requestBody;
        this.contentType = contentType;
    }

    public boolean isRepeatable() {
        return true;
    }

    public void writeRequest(OutputStream out) throws IOException {
        out.write(requestBody);
    }

    public long getContentLength() {
        return requestBody.length;
    }

    public String getContentType() {
        return contentType;
    }
}
