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
package org.orbeon.oxf.servicedirectory;

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
