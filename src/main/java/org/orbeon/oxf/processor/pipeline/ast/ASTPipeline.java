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

public class ASTPipeline extends ASTNodeContainer {

    private List<ASTParam> params = new ArrayList<ASTParam>();
    private List<ASTStatement> statements = new ArrayList<ASTStatement>();
    private Object validity;

    public List<ASTParam> getParams() {
        return params;
    }

    public ASTParam addParam(ASTParam param) {
        this.params.add(param);
        return param;
    }

    public List<ASTStatement> getStatements() {
        return statements;
    }

    public void addStatement(ASTStatement statement) {
        this.statements.add(statement);
    }

    public Object getValidity() {
        return validity;
    }

    public void setValidity(Object validity) {
        this.validity = validity;
    }

    public void walk(ASTHandler handler) {
        if (handler.startPipeline(this))
            walkChildren(handler);
        handler.endPipeline(this);
    }

    public void walkChildren(ASTHandler handler) {
        walk(params, handler);
        walk(statements, handler);
    }
}
