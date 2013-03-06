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

import org.dom4j.QName;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xml.dom4j.LocationData;

import java.util.*;

/**
 * Base interface implemented by all processors.
 *
 * @see org.orbeon.oxf.processor.ProcessorImpl
 */
public interface Processor {

    /**
     * A processor may have an "identifier". The identifier has no implication on the behaviour of
     * the processor. It is there for information only (for instance, it will be displayed by the
     * Inspector). In the pipeline language, the id is specified with the "id" attribute of the
     * <p:processor> element.
     *
     * @return the identifier of this processor
     */
    String getId();

    /**
     * @param id  the new id of this processor
     * @see #getId()
     */
    void setId(String id);

    /**
     * When this processor is created based on a declaration in an XML document, the LocationData
     * provides information about the location of this declaration. Typically, if this processor
     * corresponds to a <p:processor> a PDL file, the LocationData holds information regarding the
     * position of the <p:processor> element in the PDL file.
     *
     * @return the LocationData for this processor
     */
    LocationData getLocationData();

    /**
     * @param locationData  the new LocationData of this processor
     * @see #getLocationData()
     */
    void setLocationData(LocationData locationData);

    /**
     * Name of the processor, if it has been created by a factory and that factory has a name. The
     * name has no implication on the behaviour of the processor. It is there for information only.
     *
     * @return The name of this processor
     */
    QName getName();

    /**
     * @param  name  The new name of this processor
     * @see #getName()
     */
    void setName(QName name);

    /**
     * Creates a new input on this processor. The new input can then be connected to the output of
     * an other processor. This method cannot be called twice with the same name on the same
     * processor.
     *
     * @param   name  Name of the input to create
     * @return  The newly created input
     */
    ProcessorInput createInput(String name);

    /**
     * Deletes an input previously created with <code>createInput(String
     * name)</code>
     *
     * @param  name  Name of the input to delete
     * @see    #createInput(java.lang.String)
     */
    void deleteInput(ProcessorInput name);

    /**
     * @param   name  Name of the input
     * @return  The inputs that have been previously created with
     *          <code>createInput(String name)</code>. Returns <code>null</code>
     *          if there is no existing input with this name.
     * @see     #createInput(java.lang.String)
     */
    ProcessorInput getInputByName(String name);

    /**
     * Creates a new output on this processor. The output can then be connected
     * to the input of an other processor. This method cannnot be called twice
     * on the same processor with the same name.
     *
     * @param   name  Name of the output to create.  null is allowed.
     * @return  The newly created output
     */
    ProcessorOutput createOutput(String name);

    /**
     * Deletes an output previously created with <code>createOutput(String
     * name)</code>
     *
     * @param  output  Name of the output to delete
     * @see    #createOutput(java.lang.String)
     */
    void deleteOutput(ProcessorOutput output);

    /**
     * @param   name  Name of the output
     * @return  The outputs that have been previously created with
     *          <code>createOutput(String name)</code>. Returns
     *          <code>null</code> if there is no existing output with this name.
     * @see     #createOutput(java.lang.String)
     */
    ProcessorOutput getOutputByName(String name);

    /**
     * @return  A list of <code>ProcessorInputOutputInfo</code> objects
     *          corresponding to the inputs that can be created on this
     *          processor. This exposes the "input API" of this processor.
     */
    List<ProcessorInputOutputInfo> getInputsInfo();

    /**
     * @return  A list of <code>ProcessorInputOutputInfo</code> objects
     *          corresponding to the outputs that can be created on this
     *          processor. This exposes the "outputs API" of this processor.
     */
    List<ProcessorInputOutputInfo> getOutputsInfo();

    /**
     * @return Names of all the inputs connected to this processor.
     */
    Set<String> getInputNames();

    /**
     * @return  A read-only Map containing all the inputs currently connected
     *          to this processor. Each key in the Map is a String specifying
     *          an input name. The List associated to the key contains one or
     *          more <code>ProcessorInput</code> objects. This is particularly
     *          useful to detect whether optional inputs are connected.
     */
    Map<String, List<ProcessorInput>> getConnectedInputs();

    /**
     * @return  A read-only Map containing all the outputs currently connected
     *          to this processor. Each key in the Map is a String specifying
     *          an output name. The List associated to the key contains one or
     *          more <code>ProcessorOutput</code> objects. This is particularly
     *          useful to detect whether optional outputs are connected.
     */
    Map<String, ProcessorOutput> getConnectedOutputs();

    /**
     * TODO
     *
     * @param context
     * @param inputName
     * @return
     */
    boolean isInputInCache(PipelineContext context, String inputName);

    /**
     * TODO
     *
     * @param pipelineContext
     * @return
     */
    Object getState(PipelineContext pipelineContext);

    /**
     * This method is called to trigger the execution of this processor. This
     * method can only be called on processor with no outputs (so-called
     * serializers). If this processor has outputs, the method <code>read</code>
     * should be called on the outputs instead.
     *
     * @param  context  Context in which the processor is executed
     */
    void start(PipelineContext context);

    /**
     * Resets the processor. This method is called before the processor is
     * executed (either by calling <code>read</read> its outputs, or by calling
     * <code>start</code> on the processor.
     *
     * @param context Context in which the processor is executed
     */
    void reset(PipelineContext context);

    /**
     * TODO
     */
    int getSequenceNumber();
}
