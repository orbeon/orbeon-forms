/**
 * USWeatherSoap.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis WSDL2Java emitter.
 */

package org.orbeon.oxf.struts.examples.portlets.weather;

public interface USWeatherSoap extends java.rmi.Remote {

    /**
     * Get five day weather report for a given zipcode
     */
    public java.lang.String getWeatherReport(java.lang.String zipCode) throws java.rmi.RemoteException;
}
