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

import java.util.List;

public class OutputCacheKey extends CacheKey {

    private String outputName;
    private String key;
    private List keys;
    private int hash;

    public OutputCacheKey() {
    }

    public OutputCacheKey(org.orbeon.oxf.processor.ProcessorOutput output, String key) {
        setClazz(output.getProcessorClass());
        setOutputName(output.getName());
        setKey(key);
    }

    public OutputCacheKey(org.orbeon.oxf.processor.ProcessorOutput output, List /* <OutputCacheKey> */ keys) {
        setClazz(output.getProcessorClass());
        setOutputName(output.getName());
        setKeys(keys);
    }

    public String getOutputName() { return outputName; }
    public void setOutputName(String outputName) { this.outputName = outputName; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public List /* <OutputCacheKey> */ getKeys() { return keys; }
    public void setKeys(List /* <OutputCacheKey> */ keys) { this.keys = keys; }

    public boolean equals(Object obj) {
        boolean result = obj instanceof OutputCacheKey
                && super.equals(obj)
                && ((OutputCacheKey) obj).outputName.equals(outputName);
        return  result && (key != null
                ? ((OutputCacheKey) obj).key.equals(key)
                : ((OutputCacheKey) obj).keys.equals(keys));
    }

    public int hashCode() {
        if (hash == 0) {
            int hash = 1;
            hash += 31*hash + super.hashCode();
            hash += 31*hash + outputName.hashCode();
            hash += 31 * hash + (key != null ? key.hashCode() : keys.hashCode());
            this.hash = hash;
        }
        return hash;
    }

    public String toString() {
        return "OutputCacheKey [class: " + CacheUtils.getShortClassName(getClazz())
                + ", outputName: " + getOutputName()
                + (key != null ? ", key: " + key : ", " + keys.get(0).toString()) + "]"; 
    }
}
