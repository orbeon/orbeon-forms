/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.pipeline.api;

import java.util.Map;

/**
 * WebAppExternalContext abstracts context information so that compile-time dependencies on the
 * Servlet API or Portlet API can be removed.
 */
public interface WebAppExternalContext {

    Map<String, Object> getAttributesMap();
    Map<String, String> getInitAttributesMap();
    String getRealPath(String path);

    void log(String message, Throwable throwable);
    void log(String msg);

    Object getNativeContext();
}
