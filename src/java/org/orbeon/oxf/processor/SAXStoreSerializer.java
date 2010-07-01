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
package org.orbeon.oxf.processor;

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xml.SAXStore;

public class SAXStoreSerializer extends ProcessorImpl {

    private SAXStore saxStore = new SAXStore();

    public SAXStoreSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
    }

    public SAXStore getSAXStore() {
        return saxStore;
    }

    public void start(PipelineContext context) {
        readInputAsSAX(context, INPUT_DATA, saxStore);
    }

    public void reset(PipelineContext context) {
        saxStore = new SAXStore();
    }
}
