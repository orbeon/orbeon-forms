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
package org.orbeon.oxf.cache;

import org.orbeon.oxf.processor.ProcessorInput;

public class InputCacheKey extends CacheKey {

    private String inputName;
    private OutputCacheKey outputKey;
    private int hash;

    public InputCacheKey(ProcessorInput input, OutputCacheKey outputKey) {
        setClazz(input.getProcessorClass());
        setInputName(input.getName());
        setOutputKey(outputKey);
    }

    public String getInputName() { return inputName; }
    public void setInputName(String inputName) { this.inputName = inputName; }
    public OutputCacheKey getOutputKey() { return outputKey; }
    public void setOutputKey(OutputCacheKey outputKey) { this.outputKey = outputKey; }

    public boolean equals(Object obj) {
        return obj instanceof InputCacheKey
                && super.equals(obj)
                && ((InputCacheKey) obj).inputName.equals(inputName)
                && ((InputCacheKey) obj).outputKey.equals(outputKey);
    }

    public int hashCode() {
        if (hash == 0) {
            int hash = 1;
            hash += 31*hash + super.hashCode();
            hash += 31*hash + inputName.hashCode();
            hash += 31*hash + outputKey.hashCode();
            this.hash = hash;
        }
        return hash;
    }

    public String toString() {
        return "InputCacheKey [class: " + CacheUtils.getShortClassName(getClazz())
                + ", inputName: " + getInputName()
                + ", " + getOutputKey().toString() + "]";
    }
}
