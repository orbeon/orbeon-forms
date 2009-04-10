/**
 *  Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor;

import org.orbeon.oxf.test.ResourceManagerTestBase;
import org.orbeon.oxf.xml.XMLUtils;

import java.util.HashMap;
import java.util.Map;

public class XFormsDocumentAnnotatorContentHandlerTest extends ResourceManagerTestBase {

    public void testNamespaceElements() {

        final Map mappings = new HashMap();

        final XFormsDocumentAnnotatorContentHandler ch = new XFormsDocumentAnnotatorContentHandler(mappings);

        XMLUtils.urlToSAX("oxf:/org/orbeon/oxf/xforms/processor/test-form.xml", ch, false, false);

        // Test that ns information is provided for those elements
        Map result = (Map) mappings.get("output-in-title");
        assertNotNull(result);

        result = (Map) mappings.get("html");
        assertNotNull(result);
        
        result = (Map) mappings.get("main-instance");
        assertNotNull(result);

        result = (Map) mappings.get("dateTime-component");
        assertNotNull(result);

        result = (Map) mappings.get("dateTime1-control");
        assertNotNull(result);

        result = (Map) mappings.get("value1-control");
        assertNotNull(result);

        result = (Map) mappings.get("output-in-label");
        assertNotNull(result);

        result = (Map) mappings.get("img-in-label");
        assertNotNull(result);

        result = (Map) mappings.get("span");
        assertNotNull(result);

        // Test that ns information is NOT provided for those elements (because processed as part of shadow tree processing)
        result = (Map) mappings.get("instance-in-xbl");
        assertNull(result);

        result = (Map) mappings.get("div-in-xbl");
        assertNull(result);

        // Test that ns information is NOT provided for those elements (because in instances or schemas)
        result = (Map) mappings.get("instance-root");
        assertNull(result);

        result = (Map) mappings.get("instance-value");
        assertNull(result);

        result = (Map) mappings.get("xbl-instance-root");
        assertNull(result);

        result = (Map) mappings.get("xbl-instance-value");
        assertNull(result);

        result = (Map) mappings.get("schema-element");
        assertNull(result);
    }
}
