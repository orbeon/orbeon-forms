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
import java.util.ResourceBundle;

public class CustomerBean extends Object {

    String title = null;
    SelectItem mr = null;
    SelectItem mrs = null;
    SelectItem ms = null;
    String firstName = null;
    String middleInitial = null;
    String lastName = null;
    String mailingAddress = null;
    String city = null;
    String state = null;
    int zip;
    String month = null;
    String year = null;

    public CustomerBean() {
        super();
    }

    public void setMr(SelectItem mR) {

        this.mr = mR;
    }

    public SelectItem getMr() {

        ResourceBundle rb = ResourceBundle.getBundle("org/orbeon/faces/example/cardemo/Resources", (FacesContext.getCurrentInstance().getLocale()));
        String mRTitle = (String) rb.getObject("mrLabel");
        return new SelectItem(mRTitle, mRTitle, mRTitle);
    }

    public void setMrs(SelectItem mRs) {
        this.mrs = mRs;
    }

    public SelectItem getMrs() {
        ResourceBundle rb = ResourceBundle.getBundle("org/orbeon/faces/example/cardemo/Resources", (FacesContext.getCurrentInstance().getLocale()));
        String mRsTitle = (String) rb.getObject("mrsLabel");
        return new SelectItem(mRsTitle, mRsTitle, mRsTitle);
    }

    public void setMs(SelectItem mS) {
        this.ms = mS;
    }

    public SelectItem getMs() {
        ResourceBundle rb = ResourceBundle.getBundle("org/orbeon/faces/example/cardemo/Resources", (FacesContext.getCurrentInstance().getLocale()));
        String mSTitle = (String) rb.getObject("msLabel");
        return new SelectItem(mSTitle, mSTitle, mSTitle);
    }

    public void setFirstName(String first) {
        firstName = first;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setMiddleInitial(String mI) {
        middleInitial = mI;
    }

    public String getMiddleInitial() {
        return middleInitial;
    }

    public void setLastName(String last) {
        lastName = last;
    }

    public String getLastName() {
        return lastName;
    }

    public void setMailingAddress(String mA) {
        mailingAddress = mA;
    }

    public String getMailingAddress() {
        return mailingAddress;
    }

    public void setCity(String cty) {
        city = cty;
    }

    public String getCity() {
        return city;
    }

    public void setState(String sT) {
        state = sT;
    }

    public String getState() {
        return state;
    }

    public void setZip(int zipCode) {
        zip = zipCode;
    }

    public int getZip() {
        return zip;
    }

    public void setMonth(String mth) {
        month = mth;
    }

    public String getMonth() {
        return month;
    }

    public void setYear(String yr) {
        year = yr;
    }

    public String getYear() {
        return year;
    }
}
