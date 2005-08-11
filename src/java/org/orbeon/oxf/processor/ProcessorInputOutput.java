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
package org.orbeon.oxf.processor;

import org.orbeon.oxf.debugger.api.BreakpointKey;
import org.orbeon.oxf.xml.dom4j.LocationData;

/**
 * Why ProcessorInput and ProcessorOutput should not have a reference to their
 * corresponding processor?
 *
 * Some processors (A) create those objects and store them in cache. They can
 * be then be re-used by other processors (B) with the same configuration.
 * Obviously we don't want the ProcessorInput/ProcessorOutput to reference A
 * when it is being used by B. We could set the processor to B before running B.
 * However this doesn't work because the same configuration can be used by two
 * processors running at the same time. (This happens when an XPL makes a
 * recursive call.)
 *
 * Another option is to prevent processors from storing
 * ProcessorInput/ProcessorOutput in their configuration. This is incompatible
 * with the execution model of processors that execute (or "contain") other
 * processors. These processors store the contained processors in cache. In this
 * scenario, the processors stored in cache are connected to internal
 * ProcessorInput/ProcessorOutput, of course, also stored in cache. When
 * executed, the ProcessorOutput, also called "internal top output", find the
 * containing processor by using the processors stack stored in the
 * PipelineContext. Then the ProcessorOutput can read from the appropriate
 * input of the containing processor.
 */
public interface ProcessorInputOutput {
    public String getSchema();

    public void setSchema(String schema);

    public Class getProcessorClass();

    public String getName();

    public void setDebug(String debugMessage);

    public void setLocationData(LocationData locationData);

    public String getDebugMessage();

    public LocationData getLocationData();

    public void setBreakpointKey(BreakpointKey breakpointKey);
}
