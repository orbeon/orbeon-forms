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
package org.orbeon.oxf.processor.pipeline;


import org.orbeon.oxf.common.OXFException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the "configuration" of a pipeline: contains references to
 * the connected processors inside the pipeline and all the information
 * necessary to run the pipeline.
 */
public class PipelineConfig {

    // Maps: (String inputParamName) -> (List[InternalTopOutput internalTopOutput])
    private Map nameToTopOuputMap = new HashMap();
    // Maps: (String outputParamName) -> (ProcessorInput internalBottonInput)
    private Map nameToBottomInputMap = new HashMap();
    // All internal processors
    private List processors = new ArrayList();
    // List of Processor objects: we have to call their start() method
    private List processorsToStart = new ArrayList();

    public void declareTopOutput(String name, PipelineProcessor.InternalTopOutput topOutput) {
        List outputsForName = (List) nameToTopOuputMap.get(name);
        if (outputsForName == null) {
            outputsForName = new ArrayList();
            nameToTopOuputMap.put(name, outputsForName);
        }
        outputsForName.add(topOutput);
    }

    public Map getNameToOutputMap() {
        return nameToTopOuputMap;
    }

    public void declareBottomInput(String name, org.orbeon.oxf.processor.ProcessorInput bottomInput) {
        if (nameToBottomInputMap.containsKey(name))
            throw new OXFException("Dupplicate output parameter with name \"" + name + "\"");
        nameToBottomInputMap.put(name, bottomInput);
    }

    public Map getNameToInputMap() {
        return nameToBottomInputMap;
    }

    public void addProcessor(org.orbeon.oxf.processor.Processor processor) {
        processors.add(processor);
    }

    public List getProcessors() {
        return processors;
    }

    public void addProcessorToStart(org.orbeon.oxf.processor.Processor processor) {
        processorsToStart.add(processor);
    }

    public List getProcessorsToStart() {
        return processorsToStart;
    }
}
