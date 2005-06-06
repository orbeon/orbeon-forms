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
package org.orbeon.oxf.transformer.xupdate;

import org.dom4j.Namespace;

public class XUpdateConstants {
    public static final String XUPDATE_PREFIX = "xu";
    public static final String XUPDATE_NAMESPACE_URI = "http://www.xmldb.org/xupdate";
    public static final Namespace XUPDATE_NAMESPACE = Namespace.get(XUPDATE_PREFIX, XUPDATE_NAMESPACE_URI);
}
