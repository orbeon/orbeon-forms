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
package org.orbeon.oxf.pipeline.api;

import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;

public class TraceEntry {

    public final ProcessorOutputImpl output;

    public long start;
    public long end;

    public boolean outputReadCalled;
    public boolean outputGetKeyCalled;
    public boolean hasNullKey;

    public TraceEntry(ProcessorOutputImpl output) {
        this.output = output;
    }

    public void outputReadCalled() {

        assert !this.outputReadCalled;

        this.outputReadCalled = true;
        this.start = System.nanoTime();
    }

    public void outputGetKeyCalled(boolean hasNullKey) {
        this.outputGetKeyCalled = true;
        this.hasNullKey = hasNullKey;
    }

//    public void toXML(ContentHandlerHelper helper) {
//        helper.startElement("entry", new String[] {
//                "start", Long.toString(start),
//                "end", Long.toString(end)
//        });
//
//        if (output != null)
//            output.toXML(helper);
//
//        helper.endElement();
//    }
}
