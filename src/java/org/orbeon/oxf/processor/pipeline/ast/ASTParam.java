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
package org.orbeon.oxf.processor.pipeline.ast;

public class ASTParam extends ASTNodeContainer implements ASTDebugSchema {

    public static int INPUT = 0;
    public static int OUTPUT = 1;

    public ASTParam() {
    }

    public ASTParam(int type, String name) {
        this.type = type;
        this.name = name;
    }

    private int type;
    private String name;
    private String schemaHref;
    private String schemaUri;
    private String debug;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSchemaHref() {
        return schemaHref;
    }

    public void setSchemaHref(String schemaHref) {
        this.schemaHref = schemaHref;
    }

    public String getSchemaUri() {
        return schemaUri;
    }

    public void setSchemaUri(String schemaUri) {
        this.schemaUri = schemaUri;
    }

    public String getDebug() {
        return debug;
    }

    public void setDebug(String debug) {
        this.debug = debug;
    }

    public void walk(ASTHandler handler) {
        handler.param(this);
    }
}
