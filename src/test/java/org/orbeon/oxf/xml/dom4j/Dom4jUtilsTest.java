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
package org.orbeon.oxf.xml.dom4j;

import junit.framework.TestCase;
import org.junit.Test;
import org.orbeon.dom.Document;
import org.orbeon.dom.Element;

public class Dom4jUtilsTest extends TestCase {
    @Test
    public void testDomToString() {

        final Document document = Document.apply();
        final Element rootElement = Element.apply("div");
        document.setRootElement(rootElement);

        rootElement.addText("    ");
        final Element bElement = Element.apply("b");
        bElement.addText("bold");
        rootElement.add(bElement);
        rootElement.addText("    ");

        // Normal output
        assertEquals("<div>    <b>bold</b>    </div>", Dom4jUtils.domToStringJava(document));

        // Formatted output
        assertEquals("\n<div>\n    <b>bold</b>\n</div>", Dom4jUtils.domToPrettyStringJava(document));

        // Compact output
        assertEquals("<div><b>bold</b></div>", Dom4jUtils.domToCompactStringJava(document));
    }
}
