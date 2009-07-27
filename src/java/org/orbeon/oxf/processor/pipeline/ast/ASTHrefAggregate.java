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

public class ASTHrefAggregate extends ASTHref {

    String root;
    List<ASTHref> hrefs = new ArrayList<ASTHref>();

    public ASTHrefAggregate() {
    }

    public ASTHrefAggregate(String root, ASTHref href) {
        this.root = root;
        hrefs.add(href);
    }

    public ASTHrefAggregate(String root, ASTHref href1, ASTHref href2) {
        this.root = root;
        hrefs.add(href1);
        hrefs.add(href2);
    }

    public ASTHrefAggregate(String root, ASTHref href1, ASTHref href2, ASTHref href3) {
        this.root = root;
        hrefs.add(href1);
        hrefs.add(href2);
        hrefs.add(href3);
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public List<ASTHref> getHrefs() {
        return hrefs;
    }

    public void setHrefs(List<ASTHref> hrefs) {
        this.hrefs = hrefs;
    }

    public void walk(ASTHandler handler) {
        if (handler.startHrefAggregate(this))
            walkChildren(handler);
        handler.endHrefAggregate(this);
    }

    public void walkChildren(ASTHandler handler) {
        walk(hrefs, handler);
    }
}
