/*
 * Copyright 1998-2002, Orbeon, Inc.
 * All Rights Reserved
 *
 * This is unpublished proprietary source code of Orbeon, Inc. The
 * copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package org.orbeon.oxf.servicedirectory;

public class ServiceDefinition {

    private String name;
    private boolean hasOutputs;

    public ServiceDefinition(String name, boolean hasOutputs) {
        this.name = name;
        this.hasOutputs = hasOutputs;
    }

    public String getName() {
        return name;
    }

    public boolean hasOutputs() {
        return hasOutputs;
    }
}
