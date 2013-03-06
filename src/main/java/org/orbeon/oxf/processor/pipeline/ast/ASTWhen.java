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
package org.orbeon.oxf.processor.pipeline.ast;

import org.orbeon.oxf.xml.NamespaceMapping;

import java.util.ArrayList;
import java.util.List;

public class ASTWhen extends ASTNodeContainer {

    private String test;
    private NamespaceMapping namespaces;
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

    public NamespaceMapping getNamespaces() {
        return namespaces == null ? NamespaceMapping.EMPTY_MAPPING : namespaces;
    }

    public void setNamespaces(NamespaceMapping namespaces) {
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
