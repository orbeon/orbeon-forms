/*
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 1999 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Struts", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */


package org.apache.struts.webapp.validator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.validator.ValidatorForm;
import org.apache.struts.util.LabelValueBean;


/**
 * Form bean for the user type page.
 *
 * @author David Wintefeldt
*/
public final class TypeForm extends ValidatorForm implements Serializable {
    private String action = null;

    private String sByte = null;
    private String sShort = null;
    private String sInteger = null;
    private String sLong = null;
    private String sFloat = null;
    private String sFloatRange = null;
    private String sDouble = null;
    private String sDate = null;
    private String sCreditCard = null;

    private List lNames = initNames();

    public String getAction() {
	return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getByte() {
       return sByte;
    }

    public void setByte(String sByte) {
       	this.sByte = sByte;
    }

    public String getShort() {
       return sShort;
    }

    public void setShort(String sShort) {
       	this.sShort = sShort;
    }

    public String getInteger() {
       return sInteger;
    }

    public void setInteger(String sInteger) {
       	this.sInteger = sInteger;
    }

    public String getLong() {
       return sLong;
    }

    public void setLong(String sLong) {
       	this.sLong = sLong;
    }

    public String getFloat() {
       return sFloat;
    }

    public void setFloat(String sFloat) {
       	this.sFloat = sFloat;
    }

   /**
    * Float field with range checking
    * @return
    */
    public String getFloatRange() {
       return sFloatRange;
    }

   /**
    * Float field with range checking
    * @param sFloatRange
    */
    public void setFloatRange(String sFloatRange) {
          this.sFloatRange = sFloatRange;
    }

    public String getDouble() {
       return sDouble;
    }

    public void setDouble(String sDouble) {
       	this.sDouble = sDouble;
    }

    public String getDate() {
       return sDate;
    }

    public void setDate(String sDate) {
       	this.sDate = sDate;
    }

    public String getCreditCard() {
       return sCreditCard;
    }

    public void setCreditCard(String sCreditCard) {
       	this.sCreditCard = sCreditCard;
    }

    public List getNameList() {
       return lNames;
    }

    public void setNameList(List lNames) {
       this.lNames = lNames;
    }

    /**
     * Reset all properties to their default values.
     *
     * @param mapping The mapping used to select this instance
     * @param request The servlet request we are processing
     */
    public void reset(ActionMapping mapping, HttpServletRequest request) {
       action = null;
       sByte = null;
       sShort = null;
       sInteger = null;
       sLong = null;
       sFloat = null;
       sFloatRange = null;
       sDouble = null;
       sDate = null;
       sCreditCard = null;

       //lNames = initNames();
    }

    /**
     * Initialize list.
     * @return empty list of LabelValueBeans
    */
    private static List initNames() {
       List lResults = new ArrayList();

       for (int i = 0; i < 3; i++) {
          lResults.add(new LabelValueBean(null, null));
       }

       return lResults;
    }
}
