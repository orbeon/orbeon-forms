/**
 * USWeather.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis WSDL2Java emitter.
 */

package org.orbeon.oxf.struts.examples.portlets.weather;

public interface USWeather extends javax.xml.rpc.Service {
    public String getUSWeatherSoapAddress();

    public org.orbeon.oxf.struts.examples.portlets.weather.USWeatherSoap getUSWeatherSoap() throws javax.xml.rpc.ServiceException;

    public org.orbeon.oxf.struts.examples.portlets.weather.USWeatherSoap getUSWeatherSoap(java.net.URL portAddress) throws javax.xml.rpc.ServiceException;
}
