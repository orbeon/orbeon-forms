/*
 * Copyright 1998-2002, Orbeon, Inc.
 * All Rights Reserved
 *
 * This is unpublished proprietary source code of Orbeon, Inc. The
 * copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package org.orbeon.oxf.xmlserver;

import java.util.List;

public class XMLServerServiceDefinition {
    private String name;
    private String implementation;
    private List inputNames;
    private List outputNames;

    public XMLServerServiceDefinition(String name, String implementation, List inputNames, List outputNames) {
        this.name = name;
        this.implementation = implementation;
        this.inputNames = inputNames;
        this.outputNames = outputNames;
    }

    public String getName() {
        return name;
    }

    public String getImplementation() {
        return implementation;
    }

    public List getInputNames() {
        return inputNames;
    }

    public void setInputNames(List inputNames) {
        this.inputNames = inputNames;
    }

    public List getOutputNames() {
        return outputNames;
    }

    public void setOutputNames(List outputNames) {
        this.outputNames = outputNames;
    }
}
