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
package org.orbeon.oxf.processor.xforms.output;

public class XFormsOutputConfig {
    private String namespacePrefix;
    private String namespaceURI;

    public XFormsOutputConfig(String namespacePrefix, String namespaceURI) {
        this.namespacePrefix = namespacePrefix;
        this.namespaceURI = namespaceURI;
    }

    public String getNamespacePrefix() {
        return namespacePrefix;
    }

    public String getNamespaceURI() {
        return namespaceURI;
    }
}
