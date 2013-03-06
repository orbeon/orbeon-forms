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

/**
 * Provides information about a specific input or output of a processor.
 *
 * @see org.orbeon.oxf.processor.Processor#getInputsInfo()
 * @see org.orbeon.oxf.processor.Processor#getOutputsInfo()
 */
public class ProcessorInputOutputInfo {

    private String name;
    private String schemaURI;

    public ProcessorInputOutputInfo(String name) {
        this.name = name;
    }

    public ProcessorInputOutputInfo(String name, String schemaURI) {
        this(name);
        this.schemaURI = schemaURI;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSchemaURI() {
        return schemaURI;
    }

    public void setSchemaURI(String schemaURI) {
        this.schemaURI = schemaURI;
    }
}
