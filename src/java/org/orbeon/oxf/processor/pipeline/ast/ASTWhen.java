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
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ASTWhen extends ASTNodeContainer {

    private String test;
    private Map namespaces;
    private List<ASTStatement> statements = new ArrayList<ASTStatement>();

    public ASTWhen() {
    }

    public ASTWhen(String test) {
        this.test = test;
    }

    public String getTest() {
        return test;
    }

    public void setTest(String test) {
        this.test = test;
    }

    public Map getNamespaces() {
        return namespaces == null ? Collections.EMPTY_MAP : namespaces;
    }

    public void setNamespaces(Map<String, String> namespaces) {
        this.namespaces = namespaces;
    }

    public List<ASTStatement> getStatements() {
        return statements;
    }

    public void addStatement(ASTStatement statement) {
        this.statements.add(statement);
    }

    public void walk(ASTHandler handler) {
        if (handler.startWhen(this))
            walkChildren(handler);
        handler.endWhen(this);
    }

    public void walkChildren(ASTHandler handler) {
        walk(statements, handler);
    }
}
