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
package org.orbeon.oxf.transformer.xupdate.statement;

import org.orbeon.oxf.transformer.xupdate.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import javax.xml.transform.URIResolver;
import java.util.Arrays;

public class Text extends Statement {
    private String text;

    public Text(String text) {
        super(null);
        this.text = text;
    }

    public Object execute(URIResolver uriResolver, Object context, VariableContextImpl variableContext, DocumentContext documentContext) {
        return Arrays.asList(new Object[] {Dom4jUtils.createText(this.text)});
    }
}
