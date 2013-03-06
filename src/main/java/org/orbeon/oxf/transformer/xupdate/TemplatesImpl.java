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

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import java.util.Properties;

public class TemplatesImpl implements Templates {
    private Statement[] statements;

    public TemplatesImpl(Statement[] statements) {
        this.statements = statements;
    }

    public Statement[] getStatements() {
        return statements;
    }

    public Transformer newTransformer() {
        return new TransformerImpl(this);
    }

    public Properties getOutputProperties() {
        return null;
    }
}
