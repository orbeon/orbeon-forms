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

import java.util.HashSet;
import java.util.Set;

public class IdInfo {
    private Set inputRefs = new HashSet();
    private Set outputIds = new HashSet();
    private Set outputRefs = new HashSet();

    /**
     * All the ids references from <code>&lt;p:input href="..."></code>
     */
    public Set getInputRefs() {
        return inputRefs;
    }

    /**
     * All the ids in <code>&lt;p:output id="..."></code>
     */
    public Set getOutputIds() {
        return outputIds;
    }

    /**
     * All the refs in <code>&lt;p:output ref="..."></code>
     */
    public Set getOutputRefs() {
        return outputRefs;
    }
}
