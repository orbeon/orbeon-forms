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
package org.orbeon.oxf.struts.examples.portlets;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Node;
import org.orbeon.oxf.struts.examples.portlets.weather.USWeatherLocator;
import org.orbeon.oxf.struts.examples.portlets.weather.USWeatherSoap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class WeatherAction extends Action {

    public ActionForward perform(ActionMapping mapping,
                                 ActionForm form,
                                 HttpServletRequest request,
                                 HttpServletResponse response)
            throws IOException, ServletException {


        try {
            WeatherForm weather = (WeatherForm) form;
            String zip = weather.getZipCode();

            USWeatherSoap weatherSoap = new USWeatherLocator().getUSWeatherSoap();

            if (!zip.equals("")) {
                String xml = weatherSoap.getWeatherReport(zip);
                Document doc = DocumentHelper.parseText(xml);
                String city = ((Node) doc.selectObject("/Weather/City")).getText();
                String state = ((Node) doc.selectObject("/Weather/State")).getText();
                String condition = ((Node) doc.selectObject("/Weather/Condition")).getText();
                weather.setForecast(city + "," + state + ": " + condition);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return mapping.findForward("success");

    }
}
