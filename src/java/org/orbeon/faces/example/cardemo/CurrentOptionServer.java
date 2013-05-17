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

import javax.faces.component.SelectItem;
import javax.faces.context.FacesContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ResourceBundle;

public class CurrentOptionServer extends Object {


    protected String thisUrl = "/current.gif";
    protected int carId = 1;
    protected String carTitle = "You shouldn't see this title";
    protected String carDesc = "This description should never be seen. If it is, your properties files aren't being read.";
    protected String basePrice = "300";
    protected String currentPrice = "250";
    protected String packagePrice = "100";
    protected String engines[] = {
        "V4", "V6", "V8"
    };

    protected String brakes[] = null;
    protected String suspensions[] = null;
    protected String speakers[] = {"4", "6"};
    protected String audio[] = null;
    protected String transmissions[] = null;

    protected ArrayList engineOption;
    protected Object currentEngineOption = engines[0];
    protected ArrayList brakeOption;
    protected Object currentBrakeOption = null;
    protected ArrayList suspensionOption;
    protected Object currentSuspensionOption = null;
    protected ArrayList speakerOption;
    protected Object currentSpeakerOption = speakers[0];
    protected ArrayList audioOption;
    protected Object currentAudioOption = null;
    protected ArrayList transmissionOption;
    protected Object currentTransmissionOption = null;
    protected boolean sunRoof = false;
    protected boolean sunRoofSelected = false;
    protected boolean cruiseControl = false;
    protected boolean cruiseControlSelected = false;
    protected boolean keylessEntry = false;
    protected boolean keylessEntrySelected = false;
    protected boolean securitySystem = false;
    protected boolean securitySystemSelected = false;
    protected boolean skiRack = false;
    protected boolean skiRackSelected = false;
    protected boolean towPackage = false;
    protected boolean towPackageSelected = false;
    protected boolean gps = false;
    protected boolean gpsSelected = false;

    protected String currentPackage = "custom"; //default

    public CurrentOptionServer() {
        super();

        loadOptions();

    }

    public void loadOptions() {
        ResourceBundle rb = ResourceBundle.getBundle("org/orbeon/faces/example/cardemo/Resources", (FacesContext.getCurrentInstance().getLocale()));
        brakes = new String[2];
        brakes[0] = (String) rb.getObject("Disc");
        brakes[1] = (String) rb.getObject("Drum");

        suspensions = new String[2];
        suspensions[0] = (String) rb.getObject("Regular");
        suspensions[1] = (String) rb.getObject("Performance");

        audio = new String[2];
        audio[0] = (String) rb.getObject("Standard");
        audio[1] = (String) rb.getObject("Premium");

        transmissions = new String[2];
        transmissions[0] = (String) rb.getObject("Auto");
        transmissions[1] = (String) rb.getObject("Manual");

        currentBrakeOption = brakes[0];
        currentSuspensionOption = suspensions[0];
        currentAudioOption = audio[0];
        currentTransmissionOption = transmissions[0];

        engineOption = new ArrayList(engines.length);
        brakeOption = new ArrayList(brakes.length);
        suspensionOption = new ArrayList(suspensions.length);
        speakerOption = new ArrayList(speakers.length);
        audioOption = new ArrayList(audio.length);
        transmissionOption = new ArrayList(transmissions.length);

        int i = 0;

        for (i = 0; i < engines.length; i++) {
            engineOption.add(new SelectItem(engines[i], engines[i], engines[i]));
        }
        for (i = 0; i < brakes.length; i++) {
            brakeOption.add(new SelectItem(brakes[i], brakes[i], brakes[i]));
        }
        for (i = 0; i < suspensions.length; i++) {
            suspensionOption.add(new SelectItem(suspensions[i], suspensions[i], suspensions[i]));

        }
        for (i = 0; i < speakers.length; i++) {
            speakerOption.add(new SelectItem(speakers[i], speakers[i], speakers[i]));
        }
        for (i = 0; i < audio.length; i++) {
            audioOption.add(new SelectItem(audio[i], audio[i], audio[i]));
        }
        for (i = 0; i < transmissions.length; i++) {
            transmissionOption.add(new SelectItem(transmissions[i], transmissions[i], transmissions[i]));
        }

    }

    public void setCarId(int id) {

        try {

            ResourceBundle rb;

            // reload all properties based on car Id
            switch (id) {

                case 1:
                    // load car 1 data

                    String optionsOne = "org/orbeon/faces/example/cardemo/CarOptions1";
                    rb = ResourceBundle.getBundle(optionsOne, (FacesContext.getCurrentInstance().getLocale()));
                    setCarImage("/jsf/web/cardemo/images/200x168_Jalopy.jpg");
                    break;

                case 2:
                    // load car 2 data
                    String optionsTwo = "org/orbeon/faces/example/cardemo/CarOptions2";
                    rb = ResourceBundle.getBundle(optionsTwo, (FacesContext.getCurrentInstance().getLocale()));
                    setCarImage("/jsf/web/cardemo/images/200x168_Roadster.jpg");
                    break;

                case 3:
                    // load car 3 data
                    String optionsThree = "org/orbeon/faces/example/cardemo/CarOptions3";
                    rb = ResourceBundle.getBundle(optionsThree, (FacesContext.getCurrentInstance().getLocale()));
                    setCarImage("/jsf/web/cardemo/images/200x168_Luxury.jpg");
                    break;

                case 4:
                    // load car 4 data
                    String optionsFour = "org/orbeon/faces/example/cardemo/CarOptions4";
                    rb = ResourceBundle.getBundle(optionsFour, (FacesContext.getCurrentInstance().getLocale()));
                    setCarImage("/jsf/web/cardemo/images/200x168_SUV.jpg");
                    break;


                default:
                    // this should never happen
                    optionsOne = "org/orbeon/faces/example/cardemo/CarOptions1";
                    rb = ResourceBundle.getBundle(optionsOne, (FacesContext.getCurrentInstance().getLocale()));
                    break;
            }

            // load this bean's properties with properties from currentBundle

            this.setCarTitle((String) rb.getObject("CarTitle"));
            this.setCarDesc((String) rb.getObject("CarDesc"));
            this.setCarBasePrice((String) rb.getObject("CarBasePrice"));
            this.setCarCurrentPrice((String) rb.getObject("CarCurrentPrice"));
            loadOptions();

        } catch (Exception exc) {
            System.out.println("Exception in CurrentOptionServer: " + exc.toString());
        }
    }

    public int getCarId() {
        return carId;
    }

    public void setCarImage(String url) {
        thisUrl = url;
    }

    public String getCarImage() {
        return thisUrl;
    }

    public void setCarTitle(String title) {
        carTitle = title;
    }

    public String getCarTitle() {
        return carTitle;
    }

    public void setCarDesc(String desc) {
        carDesc = desc;
    }

    public String getCarDesc() {
        return carDesc;
    }

    public void setCarBasePrice(String bp) {
        basePrice = bp;
    }

    public String getCarBasePrice() {
        return basePrice;
    }

    public void setCarCurrentPrice(String cp) {
        currentPrice = cp;
    }

    public String getCarCurrentPrice() {
        return currentPrice;
    }

    public void setPackagePrice(String pp) {
        packagePrice = pp;
    }

    public String getPackagePrice() {
        return packagePrice;
    }

    public void setEngineOption(Collection eng) {
        engineOption = new ArrayList(eng);
    }

    public Collection getEngineOption() {
        return engineOption;
    }

    public void setCurrentEngineOption(Object eng) {
        currentEngineOption = eng;
    }

    public Object getCurrentEngineOption() {
        return currentEngineOption;
    }

    public void setBrakeOption(Collection bk) {
        brakeOption = new ArrayList(bk);
    }

    public Collection getBrakeOption() {
        return brakeOption;
    }

    public void setCurrentBrakeOption(Object op) {
        currentBrakeOption = op;
    }

    public Object getCurrentBrakeOption() {
        return currentBrakeOption;
    }

    public void setSuspensionOption(Collection op) {
        suspensionOption = new ArrayList(op);
    }

    public Collection getSuspensionOption() {
        return suspensionOption;
    }

    public void setCurrentSuspensionOption(Object op) {
        currentSuspensionOption = op;
    }

    public Object getCurrentSuspensionOption() {
        return currentSuspensionOption;
    }

    public void setSpeakerOption(Collection op) {
        speakerOption = new ArrayList(op);
    }

    public Collection getSpeakerOption() {
        return speakerOption;
    }

    public void setCurrentSpeakerOption(Object op) {
        currentSpeakerOption = op;
    }

    public Object getCurrentSpeakerOption() {
        return currentSpeakerOption;
    }

    public void setAudioOption(Collection op) {
        audioOption = new ArrayList(op);
    }

    public Collection getAudioOption() {
        return audioOption;
    }

    public void setCurrentAudioOption(Object op) {
        currentAudioOption = op;
    }

    public Object getCurrentAudioOption() {
        return currentAudioOption;
    }

    public void setTransmissionOption(Collection op) {
        transmissionOption = new ArrayList(op);
    }

    public Collection getTransmissionOption() {
        return transmissionOption;
    }

    public void setCurrentTransmissionOption(Object op) {
        currentTransmissionOption = op;
    }

    public Object getCurrentTransmissionOption() {
        return currentTransmissionOption;
    }

    public void setSunRoof(boolean roof) {
        sunRoof = roof;
    }

    public boolean getSunRoof() {
        return sunRoof;
    }

    public void setSunRoofSelected(boolean roof) {
        sunRoofSelected = roof;
    }

    public boolean getSunRoofSelected() {
        return sunRoofSelected;
    }

    public void setCruiseControl(boolean cruise) {
        cruiseControl = cruise;
    }

    public boolean getCruiseControl() {
        return cruiseControl;
    }

    public void setCruiseControlSelected(boolean cruise) {
        cruiseControlSelected = cruise;
    }

    public boolean getCruiseControlSelected() {
        return cruiseControlSelected;
    }

    public void setKeylessEntry(boolean entry) {
        keylessEntry = entry;
    }

    public boolean getKeylessEntry() {
        return keylessEntry;
    }

    public void setKeylessEntrySelected(boolean entry) {
        keylessEntrySelected = entry;
    }

    public boolean getKeylessEntrySelected() {
        return keylessEntrySelected;
    }

    public void setSecuritySystem(boolean security) {
        securitySystem = security;
    }

    public boolean getSecuritySystem() {
        return securitySystem;
    }

    public void setSecuritySystemSelected(boolean security) {
        securitySystemSelected = security;
    }

    public boolean getSecuritySystemSelected() {
        return securitySystemSelected;
    }

    public void setSkiRack(boolean ski) {
        skiRack = ski;
    }

    public boolean getSkiRack() {
        return skiRack;
    }

    public void setSkiRackSelected(boolean ski) {
        skiRackSelected = ski;
    }

    public boolean getSkiRackSelected() {
        return skiRackSelected;
    }

    public void setTowPackage(boolean tow) {
        towPackage = tow;
    }

    public boolean getTowPackage() {
        return towPackage;
    }

    public void setTowPackageSelected(boolean tow) {
        towPackageSelected = tow;
    }

    public boolean getTowPackageSelected() {
        return towPackageSelected;
    }

    public void setGps(boolean g) {
        gps = g;
    }

    public boolean getGps() {
        return gps;
    }

    public boolean getGpsSelected() {
        return gpsSelected;
    }

    public void setGpsSelected(boolean g) {
        gpsSelected = g;
    }

    public void setCurrentPackage(String pack) {
        currentPackage = pack;
    }

    public String getCurrentPackage() {
        return currentPackage;
    }
}
