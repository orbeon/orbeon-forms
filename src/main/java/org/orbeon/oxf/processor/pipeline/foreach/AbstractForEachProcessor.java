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
package org.orbeon.oxf.processor.pipeline.foreach;

import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.pipeline.ast.ASTForEach;

public class AbstractForEachProcessor extends ProcessorImpl implements AbstractProcessor {

    public static final String FOR_EACH_CURRENT_INPUT = "$current";
    public static final String FOR_EACH_DATA_INPUT = "$data";

    private ASTForEach forEachAST;
    private Object validity;

    public AbstractForEachProcessor(ASTForEach forEachAST, Object validity) {
        this.forEachAST = forEachAST;
        this.validity = validity;
        setLocationData(forEachAST.getLocationData());
    }

    public Processor createInstance() {
        return new ConcreteForEachProcessor(forEachAST, validity);
    }

}
