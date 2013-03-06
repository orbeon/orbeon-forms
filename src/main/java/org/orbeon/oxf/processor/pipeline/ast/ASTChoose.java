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
package org.orbeon.oxf.processor.pipeline.ast;

import java.util.ArrayList;
import java.util.List;

public class ASTChoose extends ASTStatement implements ASTDebugSchema {

    private ASTHref href;
    private List<ASTWhen> when = new ArrayList<ASTWhen>();
    private String schemaHref;
    private String schemaUri;
    private String debug;

    public ASTChoose() {
    }

    public ASTChoose(ASTHref href) {
        this.href = href;
    }

    public ASTHref getHref() {
        return href;
    }

    public void setHref(ASTHref href) {
        this.href = href;
    }

    public List<ASTWhen> getWhen() {
        return when;
    }

    public void addWhen(ASTWhen when) {
        this.when.add(when);
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
        if (handler.startChoose(this))
            walkChildren(handler);
        handler.endChoose(this);
    }

    public void walkChildren(ASTHandler handler) {
        href.walk(handler);
        walk(when, handler);
    }

}
