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
package org.orbeon.oxf.xml.xerces;

/**
 * This is our own version of XIncludeHandler that supports a listener to report inclusions.
 *
 * NOTE: As of 2007-07-12, we have removed the old code dealing with features, and reverted to modifying the base class
 * in Xerces to call the listener defined here. This has the drawback of having to modify Xerces, but the experience of
 * upgrading to Xerces 2.9 shows that we don't gain much by choosing the alternative of copying over some Xerces code to
 * here as was done before.
 */
public class XIncludeHandler extends orbeon.apache.xerces.xinclude.XIncludeHandler {

    public XIncludeHandler() {
    }
}
