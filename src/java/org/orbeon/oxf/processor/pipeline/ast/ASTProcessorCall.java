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

import org.dom4j.QName;
import org.orbeon.oxf.processor.Processor;

import java.util.ArrayList;
import java.util.List;

public class ASTProcessorCall extends ASTStatement {

    public ASTProcessorCall() {
    }

    public ASTProcessorCall(QName name) {
        this.name = name;
    }

    public ASTProcessorCall(String uri) {
        this.uri = uri;
    }

    public ASTProcessorCall(QName name, String uri) {
        this.name = name;
        this.uri = uri;
    }

    public ASTProcessorCall(Processor processor) {
        this.processor = processor;
    }

    private List inputs = new ArrayList();
    private List outputs = new ArrayList();
    private QName name;
    private String uri;
    private Processor processor;
    private String id;
    private String encapsulation;

    public void addInput(ASTInput input) {
        inputs.add(input);
    }

    public List getInputs() {
        return inputs;
    }

    public void addOutput(ASTOutput output) {
        outputs.add(output);
    }

    public List getOutputs() {
        return outputs;
    }

    public QName getName() {
        return name;
    }

    public void setName(QName name) {
        this.name = name;
    }

    public String getURI() {
        return uri;
    }

    public void setURI(String uri) {
        this.uri = uri;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEncapsulation() {
        return encapsulation;
    }

    public void setEncapsulation(String encapsulation) {
        this.encapsulation = encapsulation;
    }

    public Processor getProcessor() {
        return processor;
    }

    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    public void walk(ASTHandler handler) {
        if (handler.startProcessorCall(this))
            walkChildren(handler);
        handler.endProcessorCall(this);
    }

    public void walkChildren(ASTHandler handler) {
        walk(inputs, handler);
        walk(outputs, handler);
    }
}
