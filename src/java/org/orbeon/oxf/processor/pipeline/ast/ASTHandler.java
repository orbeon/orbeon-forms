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


public interface ASTHandler {
    public boolean startPipeline(ASTPipeline pipeline);

    public void endPipeline(ASTPipeline pipeline);

    public void param(ASTParam param);

    public boolean startProcessorCall(ASTProcessorCall processorCall);

    public void endProcessorCall(ASTProcessorCall processorCall);

    public boolean startInput(ASTInput input);

    public void endInput(ASTInput input);

    public void output(ASTOutput output);

    public boolean startHrefAggregate(ASTHrefAggregate hrefAggregate);

    public void endHrefAggregate(ASTHrefAggregate hrefAggregate);

    public void hrefId(ASTHrefId hrefId);

    public void hrefURL(ASTHrefURL hrefURL);

    public boolean startHrefXPointer(ASTHrefXPointer hrefXPointer);

    public void endHrefXPointer(ASTHrefXPointer hrefXPointer);

    public boolean startChoose(ASTChoose choose);

    public void endChoose(ASTChoose choose);

    public boolean startForEach(ASTForEach forEach);

    public void endStartForEach(ASTForEach forEach);

    public void endForEach(ASTForEach forEach);

    public boolean startWhen(ASTWhen when);

    public void endWhen(ASTWhen when);
}
