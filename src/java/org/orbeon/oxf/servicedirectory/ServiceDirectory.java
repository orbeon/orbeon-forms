/*
 * Copyright 1998-2002, Orbeon, Inc.
 * All Rights Reserved
 *
 * This is unpublished proprietary source code of Orbeon, Inc. The
 * copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package org.orbeon.oxf.servicedirectory;

import org.orbeon.oxf.servicedirectory.ServiceDefinition;

import java.util.*;

public class ServiceDirectory {

    private static ServiceDirectory instance = new ServiceDirectory();
    private List services = new ArrayList();
    private Map name = new HashMap();

    private ServiceDirectory() {
    }

    public static ServiceDirectory instance() {
        return instance;
    }

    public void addServiceDefinition(ServiceDefinition service) {
        services.add(service);
        name.put(service.getName(), service);
    }

    public ServiceDefinition getServiceByName(String name) {
        return (ServiceDefinition) this.name.get(name);
    }

    public List getServiceDefinitions() {
        return services;
    }
}
