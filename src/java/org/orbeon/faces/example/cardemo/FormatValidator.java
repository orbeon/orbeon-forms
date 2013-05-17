/*
 * $Id: FormatValidator.java,v 1.1.1.1 2004/08/21 20:05:51 ebruchez Exp $
 */

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

import javax.faces.FactoryFinder;
import javax.faces.component.UIComponent;
import javax.faces.component.UIOutput;
import javax.faces.context.FacesContext;
import javax.faces.application.Message;
import javax.faces.context.MessageResources;
import javax.faces.validator.Validator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;

import javax.faces.application.ApplicationFactory;
import javax.faces.application.Application;


/**
 * <p><strong>FormatValidator</strong> is a Validator that checks
 * the validity of String representation of the value of the
 * associated component against a list of specified patterns.</p>
 * <ul>
 * <li>Call getValue() to retrieve the current value of the component.
 *     If it is <code>null</code>, exit immediately.  (If null values
 *     should not be allowed, a RequiredValidator can be configured
 *     to check for this case.)</li>
 * <li><code>formatPattern</code> is a <code>|</code> separated string
 * of allowed patterns. </li>
 * <li> This validator uses the following rules to match a value against a
 * pattern.
 * <li> if the matching pattern has a "A", then corresponding character
 *      in input value should be a letter.
 * <li> if the matching pattern has a "9", then corresponding character
 *      in input value should be a number.
 * <li> if the matching pattern has a "#", then corresponding character
 *      in input value should be a number or  a letter.
 * <li> Any other character must match literally.
 * </ul> </ul>
 *
 * Validators have to be Serializable, so you can't maintain a reference to
 * a java.sql.Connection or javax.sql.DataSource inside this class in case
 * you need to hook upto the database or some other back end resource.
 * One approach would be to use JNDI-based data source lookups or do
 * this verification in the business tier.
 */

public class FormatValidator implements Validator {

    /**
     * <p>The message identifier of the Message to be created if
     * the validation fails.  The message format string for this
     * message may optionally include a <code>{0}</code> placeholder, which
     * will be replaced by list of format patterns.</p>
     */
    public static final String FORMAT_INVALID_MESSAGE_ID =
            "cardemo.Format_Invalid";

    private ArrayList formatPatternsList = null;

    public FormatValidator() {
        super();
    }

    /**
     * <p>Construct a FormatValidator with the specified formatPatterns
     * String. </p>
     *
     * @param formatPatterns <code>|</code> separated String of format patterns
     *        that this validator must match against.
     *
     */
    public FormatValidator(String formatPatterns) {
        super();
        this.formatPatterns = formatPatterns;
        parseFormatPatterns();
    }

    /**
     * <code>|</code> separated String of format patterns
     * that this validator must match against.
     */
    private String formatPatterns = null;

    /**
     * <p>Return the format patterns that the validator supports.
     */
    public String getFormatPatterns() {

        return (this.formatPatterns);

    }

    /**
     * <p>Set the format patterns that the validator support..</p>
     *
     * @param formatPatterns <code>|</code> separated String of format patterns
     *        that this validator must match against.
     *
     */
    public void setFormatPatterns(String formatPatterns) {

        this.formatPatterns = formatPatterns;
        parseFormatPatterns();
    }

    /**
     * Parses the <code>formatPatterns</code> into validPatterns
     * <code>ArrayList</code>. The delimiter must be "|".
     */
    public void parseFormatPatterns() {
        if (formatPatterns == null || formatPatterns.length() == 0) {
            return;
        }
        if (formatPatternsList != null) {
            // formatPatterns have been parsed already.
            return;
        } else {
            formatPatternsList = new ArrayList();
        }
        StringTokenizer st = new StringTokenizer(formatPatterns, "|");
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            formatPatternsList.add(token);
        }
    }

    public void validate(FacesContext context, UIComponent component) {
        boolean valid = false;

        if ((context == null) || (component == null)) {
            throw new NullPointerException();
        }
        if (!(component instanceof UIOutput)) {
            return;
        }

        if (formatPatternsList == null) {
            // no patterns to match
            component.setValid(true);
            return;
        }

        String value = (((UIOutput) component).getValue()).toString();
        // validate the value against the list of valid patterns.
        Iterator patternIt = formatPatternsList.iterator();
        while (patternIt.hasNext()) {
            valid = isFormatValid(((String) patternIt.next()), value);
            if (valid) {
                break;
            }
        }
        if (valid) {
            component.setValid(true);
        } else {
            component.setValid(false);
            Message errMsg = getMessageResources().getMessage(context,
                    FORMAT_INVALID_MESSAGE_ID,
                    (new Object[]{formatPatterns}));
            context.addMessage(component, errMsg);
        }
    }

    /**
     * Returns true if the value matches one of the valid patterns.
     */
    protected boolean isFormatValid(String pattern, String value) {
        boolean valid = true;
        // if there is no pattern to match then value is valid
        if (pattern == null || pattern.length() == 0) {
            return true;
        }
        // if the value is null or a zero length string return false.
        if (value == null || value.length() == 0) {
            return false;
        }
        // if the length of the value is not equal to the length of the
        // pattern string then the value is not valid.
        if (value.length() != pattern.length()) {
            return false;
        }
        value = value.trim();
        // rules for matching.
        // 1. if the matching pattern has a "A", then corresponding character
        // in the value should a letter.
        // 2. if the matching pattern has a "9", then corresponding character
        // in the value should a number
        // 3. if the matching pattern has a "#", then corresponding character
        // in the value should a number or a letter
        // 4.. any other character must match literally.
        char[] input = value.toCharArray();
        char[] fmtpattern = pattern.toCharArray();
        for (int i = 0; i < fmtpattern.length; ++i) {
            if (fmtpattern[i] == 'A') {
                if (!(Character.isLetter(input[i]))) {
                    valid = false;
                }
            } else if (fmtpattern[i] == '9') {
                if (!(Character.isDigit(input[i]))) {
                    valid = false;
                }
            } else if (fmtpattern[i] == '#') {
                if ((!(Character.isDigit(input[i]))) &&
                        (!(Character.isLetter(input[i])))) {
                    valid = false;
                }
            } else {
                if (!(fmtpattern[i] == input[i])) {
                    valid = false;
                }
            }
        }
        return valid;

    }

    /**
     * This method will be called before calling facesContext.addMessage, so
     * message can be localized.
     * <p>Return the {@link MessageResources} instance for the message
     * resources defined by the JavaServer Faces Specification.
     */
    public synchronized MessageResources getMessageResources() {
        MessageResources carResources = null;
        ApplicationFactory aFactory =
                (ApplicationFactory) FactoryFinder.getFactory(FactoryFinder.APPLICATION_FACTORY);
        Application application = aFactory.getApplication();

        carResources = application.getMessageResources("carDemoResources");
        return (carResources);
    }
}
