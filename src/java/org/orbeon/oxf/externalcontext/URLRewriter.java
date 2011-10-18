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
package org.orbeon.oxf.externalcontext;

public interface URLRewriter {

    // Works as a bitset
    // 1: whether to produce an absolute URL (starting with "http" or "https")
    // 2: whether to leave the URL as is if it is does not start with "/"
    // 4: whether to prevent insertion of a context at the start of the path
    static final int REWRITE_MODE_ABSOLUTE = 1;
    static final int REWRITE_MODE_ABSOLUTE_PATH = 0;
    static final int REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE = 2;
    static final int REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT = 4;
    static final int REWRITE_MODE_ABSOLUTE_NO_CONTEXT = 5;

    String rewriteRenderURL(String urlString);
    String rewriteRenderURL(String urlString, String portletMode, String windowState);
    String rewriteActionURL(String urlString);
    String rewriteActionURL(String urlString, String portletMode, String windowState);
    String rewriteResourceURL(String urlString, int rewriteMode);
    String getNamespacePrefix();
}
