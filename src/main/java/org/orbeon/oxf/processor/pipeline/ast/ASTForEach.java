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

public class ASTForEach extends ASTStatement {

    private ASTHref href;
    private String select;
    private String id;
    private String ref;
    private String root;
    private String inputSchemaHref;
    private String inputSchemaUri;
    private String inputDebug;
    private String outputSchemaHref;
    private String outputSchemaUri;
    private String outputDebug;
    private List<ASTStatement> statements = new ArrayList<ASTStatement>();

    public ASTHref getHref() {
        return href;
    }

    public void setHref(ASTHref href) {
        this.href = href;
    }

    public String getSelect() {
        return select;
    }

    public void setSelect(String select) {
        this.select = select;
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

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public String getInputSchemaHref() {
        return inputSchemaHref;
    }

    public void setInputSchemaHref(String inputSchemaHref) {
        this.inputSchemaHref = inputSchemaHref;
    }

    public String getInputSchemaUri() {
        return inputSchemaUri;
    }

    public void setInputSchemaUri(String inputSchemaUri) {
        this.inputSchemaUri = inputSchemaUri;
    }

    public String getInputDebug() {
        return inputDebug;
    }

    public void setInputDebug(String inputDebug) {
        this.inputDebug = inputDebug;
    }

    public String getOutputSchemaHref() {
        return outputSchemaHref;
    }

    public void setOutputSchemaHref(String outputSchemaHref) {
        this.outputSchemaHref = outputSchemaHref;
    }

    public String getOutputSchemaUri() {
        return outputSchemaUri;
    }

    public void setOutputSchemaUri(String outputSchemaUri) {
        this.outputSchemaUri = outputSchemaUri;
    }

    public String getOutputDebug() {
        return outputDebug;
    }

    public void setOutputDebug(String outputDebug) {
        this.outputDebug = outputDebug;
    }

    public List<ASTStatement> getStatements() {
        return statements;
    }

    public void addStatement(ASTStatement statement) {
        this.statements.add(statement);
    }

    public void walk(ASTHandler handler) {
        if (handler.startForEach(this))
            walkChildren(handler);
        handler.endForEach(this);
    }

    public void walkChildren(ASTHandler handler) {
        href.walk(handler);
        handler.endStartForEach(this);
        walk(statements, handler);
    }
}
