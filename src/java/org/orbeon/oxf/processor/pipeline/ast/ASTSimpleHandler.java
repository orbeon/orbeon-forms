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


public class ASTSimpleHandler implements ASTHandler {
    public boolean startPipeline(ASTPipeline pipeline) {
        return true;
    }

    public void endPipeline(ASTPipeline pipeline) {
    }

    public void param(ASTParam param) {
    }

    public boolean startProcessorCall(ASTProcessorCall processorCall) {
        return true;
    }

    public void endProcessorCall(ASTProcessorCall processorCall) {
    }

    public boolean startInput(ASTInput input) {
        return true;
    }

    public void endInput(ASTInput input) {
    }

    public void output(ASTOutput output) {
    }

    public boolean startHrefAggregate(ASTHrefAggregate hrefAggregate) {
        return true;
    }

    public void endHrefAggregate(ASTHrefAggregate hrefAggregate) {
    }

    public void hrefId(ASTHrefId hrefId) {
    }

    public void hrefURL(ASTHrefURL hrefURL) {
    }

    public boolean startChoose(ASTChoose choose) {
        return true;
    }

    public void endChoose(ASTChoose choose) {
    }

    public boolean startForEach(ASTForEach forEach) {
        return true;
    }

    public void endStartForEach(ASTForEach forEach) {
    }

    public void endForEach(ASTForEach forEach) {
    }

    public boolean startWhen(ASTWhen when) {
        return true;
    }

    public void endWhen(ASTWhen when) {
    }

    public boolean startHrefXPointer(ASTHrefXPointer hrefXPointer) {
        return true;
    }

    public void endHrefXPointer(ASTHrefXPointer hrefXPointer) {
    }
}
