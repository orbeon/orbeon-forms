/**
 * USWeatherLocator.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis WSDL2Java emitter.
 */

package org.orbeon.oxf.struts.examples.portlets.weather;

public class USWeatherLocator extends org.apache.axis.client.Service implements org.orbeon.oxf.struts.examples.portlets.weather.USWeather {

    // Use to get a proxy class for USWeatherSoap
    private final java.lang.String USWeatherSoap_address = "http://www.webservicex.net/usweather.asmx";

    public String getUSWeatherSoapAddress() {
        return USWeatherSoap_address;
    }

    public org.orbeon.oxf.struts.examples.portlets.weather.USWeatherSoap getUSWeatherSoap() throws javax.xml.rpc.ServiceException {
       java.net.URL endpoint;
        try {
            endpoint = new java.net.URL(USWeatherSoap_address);
        }
        catch (java.net.MalformedURLException e) {
            return null; // unlikely as URL was validated in WSDL2Java
        }
        return getUSWeatherSoap(endpoint);
    }

    public org.orbeon.oxf.struts.examples.portlets.weather.USWeatherSoap getUSWeatherSoap(java.net.URL portAddress) throws javax.xml.rpc.ServiceException {
        try {
            return new org.orbeon.oxf.struts.examples.portlets.weather.USWeatherSoapStub(portAddress, this);
        }
        catch (org.apache.axis.AxisFault e) {
            return null; // ???
        }
    }

    /**
     * For the given interface, get the stub implementation.
     * If this service has no port for the given interface,
     * then ServiceException is thrown.
     */
    public java.rmi.Remote getPort(Class serviceEndpointInterface) throws javax.xml.rpc.ServiceException {
        try {
            if (org.orbeon.oxf.struts.examples.portlets.weather.USWeatherSoap.class.isAssignableFrom(serviceEndpointInterface)) {
                return new org.orbeon.oxf.struts.examples.portlets.weather.USWeatherSoapStub(new java.net.URL(USWeatherSoap_address), this);
            }
        }
        catch (Throwable t) {
            throw new javax.xml.rpc.ServiceException(t);
        }
        throw new javax.xml.rpc.ServiceException("There is no stub implementation for the interface:  " + (serviceEndpointInterface == null ? "null" : serviceEndpointInterface.getName()));
    }

}
