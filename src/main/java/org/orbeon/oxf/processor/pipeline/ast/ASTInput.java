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

import org.dom4j.Node;
import org.dom4j.QName;

public class ASTInput extends ASTInputOutput {

    private ASTHref href;
    private QName transform;

    public ASTInput() {
    }

    public ASTInput(String name, ASTHref href) {
        setName(name);
        this.href = href;
    }

    public ASTInput(String name, Node content) {
        setName(name);
        setContent(content);
    }

    public ASTHref getHref() {
        return href;
    }

    public void setHref(ASTHref href) {
        this.href = href;
    }

    public QName getTransform() {
        return transform;
    }

    public void setTransform(QName transform) {
        this.transform = transform;
    }

    public void walk(ASTHandler handler) {
        if (handler.startInput(this))
            walkChildren(handler);
        handler.endInput(this);
    }

    public void walkChildren(ASTHandler handler) {
        if (href != null) href.walk(handler);
    }
}
