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
package org.orbeon.oxf.processor.transformer.xslt;

public class XSLT1Transformer extends XSLTTransformer {

    // NOTE: This is an unofficial pseudo-URI, used by the Schema Repository
    public static final String XSLT_1_0_PSEUDO_URI = "http://www.w3.org/1999/XSL/Transform|1.0";

    public XSLT1Transformer() {
        // Set the schema for validation
        super(XSLT_1_0_PSEUDO_URI);
    }
}
