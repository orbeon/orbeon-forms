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


public class ASTOutput extends ASTInputOutput {

    private String id;
    private String ref;

    public ASTOutput() {
    }

    public ASTOutput(String name, String id) {
        setName(name);
        this.id = id;
    }

    public ASTOutput(String name, ASTParam param) {
        this( name, ( String )null );
        ref = param.getName();
    }

    public ASTOutput(String name, ASTOutput output) {
        this( name, output.id );
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public void walk(ASTHandler handler) {
        handler.output(this);
    }
}
