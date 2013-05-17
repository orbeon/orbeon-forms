/*
 * Copyright 2002, 2003 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistribution in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials
 *   provided with the distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY
 * DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT OF OR
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THIS SOFTWARE OR
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS
 * BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 */

package org.orbeon.faces.example.cardemo;

import com.sun.faces.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.faces.context.FacesContext;
import javax.faces.event.PhaseId;
import javax.faces.event.ValueChangedEvent;
import javax.faces.event.ValueChangedListener;
import java.util.ResourceBundle;

/**
 * PackageValueChanged gets called when any of the package options for a
 * car in the more.jsp page changes
 */
public class PackageValueChanged implements ValueChangedListener {

    private static Log log = LogFactory.getLog(PackageValueChanged.class);

    /** Creates a new instance of PackageValueChanged */
    public PackageValueChanged() {
    }


    public PhaseId getPhaseId() {
        return PhaseId.PROCESS_VALIDATIONS;
    }


    public void processValueChanged(ValueChangedEvent vEvent) {
        try {
            log.debug("ValueChangedEvent processEvent");
            String componentId = vEvent.getComponent().getComponentId();
            // handle each valuechangedevent here
            FacesContext context = FacesContext.getCurrentInstance();
            String currentPrice;
            int cPrice = 0;
            currentPrice = (String)
                    (Util.getValueBinding("CurrentOptionServer.carCurrentPrice"))
                    .getValue(context);
            cPrice = Integer.parseInt(currentPrice);
            log.debug("Component Id: " + componentId);
            log.debug("vEvent.getOldValue: " + vEvent.getOldValue());
            log.debug("vEvent.getNewValue: " + vEvent.getNewValue());

            log.debug("Vevent name: " + (vEvent.getNewValue()).getClass().getName());
            // the if is for the SelectItems; else is for checkboxes
            if ((componentId.equals("currentEngine")) ||
                    (componentId.equals("currentBrake")) ||
                    (componentId.equals("currentSuspension")) ||
                    (componentId.equals("currentSpeaker")) ||
                    (componentId.equals("currentAudio")) ||
                    (componentId.equals("currentTransmission"))) {
                log.debug("vEvent.getOldValue: " + vEvent.getOldValue());
                log.debug("vEvent.getNewValue: " + vEvent.getNewValue());

                cPrice = cPrice - (this.getPriceFor((String) vEvent.getOldValue()));
                cPrice = cPrice + (this.getPriceFor((String) vEvent.getNewValue()));
                //cPrice = cPrice + 100;
            } else {

                Boolean optionSet = (Boolean) vEvent.getNewValue();
                cPrice = calculatePrice(componentId, optionSet, cPrice);
            }

            // update model value
            currentPrice = Integer.toString(cPrice);
            (Util.getValueBinding("CurrentOptionServer.carCurrentPrice")).
                    setValue(context, currentPrice);
        } catch (NumberFormatException ignored) {
        }

    }

    public int calculatePrice(String optionKey, Boolean optionSet, int cPrice) {
        if (optionSet.equals(Boolean.TRUE)) {
            cPrice = cPrice + (this.getPriceFor(optionKey));
        } else {
            cPrice = cPrice - (this.getPriceFor(optionKey));
        }
        return cPrice;
    }

    //PENDING(rajprem): this information should eventually
    //go into CarOptionsn.properties
    public int getPriceFor(String option) {
        ResourceBundle rb = ResourceBundle.getBundle(
                "org/orbeon/faces/example/cardemo/Resources", (FacesContext.getCurrentInstance().getLocale()));

        if (option.equals("V4")) {
            return (100);
        } else if (option.equals("V6")) {
            return (200);
        } else if (option.equals("V8")) {
            return (300);
        } else if (option.equals((String) rb.getObject("Disc"))) {
            return (100);
        } else if (option.equals((String) rb.getObject("Drum"))) {
            return (200);
        } else if (option.equals((String) rb.getObject("Regular"))) {
            return (150);
        } else if (option.equals((String) rb.getObject("Performance"))) {
            return (300);
        } else if (option.equals("4")) {
            return (100);
        } else if (option.equals("6")) {
            return (200);
        } else if (option.equals((String) rb.getObject("Standard"))) {
            return (100);
        } else if (option.equals((String) rb.getObject("Premium"))) {
            return (200);
        } else if (option.equals((String) rb.getObject("Auto"))) {
            return (300);
        } else if (option.equals((String) rb.getObject("Manual"))) {
            return (200);
        } else if (option.equals("sunroof")) {
            return (100);
        } else if (option.equals("cruisecontrol")) {
            return (150);
        } else if (option.equals("keylessentry")) {
            return (100);
        } else if (option.equals("securitySystem")) {
            return (100);
        } else if (option.equals("skirack")) {
            return (200);
        } else if (option.equals("towPackage")) {
            return (200);
        } else if (option.equals("gps")) {
            return (200);
        } else
            return 0;
    }
}
