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
package org.orbeon.oxf.proxy;

import org.orbeon.oxf.common.OXFException;

import javax.ejb.CreateException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;
import java.rmi.RemoteException;
import java.util.Map;


public class ProxyServiceDelegate {

    private final String SERVICE_JNDI_NAME = "java:comp/env/ejb/oxf/proxy";
    private ProxyService service;

    public ProxyServiceDelegate(Context jndiContext) {
        try {
            Object serviceHomeRef = jndiContext.lookup(SERVICE_JNDI_NAME);
            ProxyServiceHome home =
                (ProxyServiceHome) PortableRemoteObject.narrow
                (serviceHomeRef, ProxyServiceHome.class);
            service = home.create();
        } catch (CreateException e) {
            throw new OXFException(e);
        } catch (RemoteException e) {
            throw new OXFException(e);
        } catch (NamingException e) {
            throw new OXFException(e);
        }
    }

    public void setJNDIName(String jndiName) {
        try {
            service.setJNDIName(jndiName);
        } catch (RemoteException e) {
            throw new OXFException(e);
        }
    }

    public void setInputs(Map inputs) {
        try {
            service.setInputs(inputs);
        } catch (RemoteException e) {
            throw new OXFException(e);
        }
    }

    public Map getOutputs() {
        try {
            return service.getOutputs();
        } catch (RemoteException e) {
            throw new OXFException(e);
        }
    }
}
