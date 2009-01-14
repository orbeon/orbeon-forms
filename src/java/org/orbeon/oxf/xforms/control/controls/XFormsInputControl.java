/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.MatchProcessor;
import org.orbeon.oxf.processor.Perl5MatchProcessor;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.value.DateValue;
import org.orbeon.saxon.value.TimeValue;
import org.orbeon.saxon.value.CalendarValue;
import org.xml.sax.helpers.AttributesImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.GregorianCalendar;
import java.util.Calendar;

/**
 * Represents an xforms:input control.
 */
public class XFormsInputControl extends XFormsValueControl {

    // TODO: would be nice to make extension attributes generic instead of hardcoded
//    private static final String[] XXFORMS_ATTRIBUTES = { "size", "maxlength", "autocomplete" };

    private boolean isSizeEvaluated;
    private boolean isMaxlengthEvaluated;
    private boolean isAutocompleteEvaluated;

    private String size;
    private String maxlength;
    private String autocomplete;

    public XFormsInputControl(XFormsContainer container, XFormsControl parent, Element element, String name, String id) {
        super(container, parent, element, name, id);
    }

    protected void evaluate(PipelineContext pipelineContext) {
        super.evaluate(pipelineContext);

        getSize(pipelineContext);
        getMaxlength(pipelineContext);
        getAutocomplete(pipelineContext);
    }

    public void markDirty() {
        super.markDirty();
        isSizeEvaluated = false;
        isMaxlengthEvaluated = false;
        isAutocompleteEvaluated = false;
    }

    public String getSize(PipelineContext pipelineContext) {
        if (!isSizeEvaluated) {
            final String attributeValue = getControlElement().attributeValue(XFormsConstants.XXFORMS_SIZE_QNAME);
            size = (attributeValue == null) ? null : evaluateAvt(pipelineContext, attributeValue);
            isSizeEvaluated = true;
        }
        return size;
    }

    public String getMaxlength(PipelineContext pipelineContext) {
        if (!isMaxlengthEvaluated) {
            final String attributeValue = getControlElement().attributeValue(XFormsConstants.XXFORMS_MAXLENGTH_QNAME);
            maxlength = (attributeValue == null) ? null : evaluateAvt(pipelineContext, attributeValue);
            isMaxlengthEvaluated = true;
        }
        return maxlength;
    }

    public String getAutocomplete(PipelineContext pipelineContext) {
        if (!isAutocompleteEvaluated) {
            final String attributeValue = getControlElement().attributeValue(XFormsConstants.XXFORMS_AUTOCOMPLETE_QNAME);
            autocomplete = (attributeValue == null) ? null : evaluateAvt(pipelineContext, attributeValue);
            isAutocompleteEvaluated = true;
        }
        return autocomplete;
    }

    protected void evaluateExternalValue(PipelineContext pipelineContext) {

        final String internalValue = getValue(pipelineContext);
        final String updatedValue;

        final String typeName = getBuiltinTypeName();
        if (typeName != null) {
            if (typeName.equals("boolean")) {
                // xs:boolean

                if (internalValue != null && !internalValue.equals("true")) {
                    // This so we don't send "false" to the client but ""
                    updatedValue = "";
                } else {
                    if (XFormsProperties.isEncryptItemValues(containingDocument)) {
                        // Encrypt outgoing value if needed
                        updatedValue = XFormsItemUtils.encryptValue(pipelineContext, internalValue);
                    } else {
                        // For open selection, values sent to client are the internal values
                        updatedValue = internalValue;
                    }
                }
            } else {
                // Other types
                updatedValue = internalValue;
            }
        } else {
            // No type
            updatedValue = internalValue;
        }

        setExternalValue(updatedValue);
    }

    public void storeExternalValue(PipelineContext pipelineContext, String value, String type) {
        super.storeExternalValue(pipelineContext, convertFromExternalValue(pipelineContext, value), type);
    }

    private String convertFromExternalValue(PipelineContext pipelineContext, String externalValue) {
        final String typeName = getBuiltinTypeName();
        if (typeName != null) {
            if (typeName.equals("boolean")) {
                // Boolean input

                // Decrypt incoming value if needed. With open selection, values are sent to the client.
                if (XFormsProperties.isEncryptItemValues(containingDocument))
                    externalValue = XFormsItemUtils.decryptValue(pipelineContext, externalValue);

                // Anything but "true" is "false"
                if (!externalValue.equals("true"))
                    externalValue = "false";
            } else if (XFormsProperties.isNoscript(containingDocument)) {
                // Noscript mode: value must be pre-processed on the server (in Ajax mode, ISO value is sent to server if possible)

                if ( "date".equals(typeName)) {
                    // Date input
                    externalValue = externalValue.trim();
                    final Perl5MatchProcessor matcher = new Perl5MatchProcessor();
                    externalValue = parse(matcher, DATE_PARSE_PATTERNS, externalValue);
                } else if ("time".equals(typeName)) {
                    // Time input
                    externalValue = externalValue.trim();
                    final Perl5MatchProcessor matcher = new Perl5MatchProcessor();
                    externalValue = parse(matcher, TIME_PARSE_PATTERNS, externalValue);
                } else if ("dateTime".equals(typeName)) {
                    // Date + time input
                    externalValue = externalValue.trim();

                    // Split into date and time parts
                    // We use the same separator as the repeat separator. This is set in xforms-server-submit.xpl.
                    final String datePart = getDateTimeDatePart(externalValue, XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
                    final String timePart = getDateTimeTimePart(externalValue, XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);

                    if (datePart.length() == 0 && timePart.length() == 0) {
                        // Special case of empty parts
                        externalValue = "";;
                    } else {
                        // Parse and recombine with 'T' separator (result may be invalid dateTime, of course!)
                        final Perl5MatchProcessor matcher = new Perl5MatchProcessor();
                        externalValue = parse(matcher, DATE_PARSE_PATTERNS, datePart) + 'T' + parse(matcher, TIME_PARSE_PATTERNS, timePart);
                    }
                }
            }
        }

        return externalValue;
    }

    private static String getDateTimeDatePart(String value, char separator) {
        final int separatorIndex = value.indexOf(separator);
        if (separatorIndex == -1) {
            return value;
        } else {
            return value.substring(0, separatorIndex).trim();
        }
    }

    private static String getDateTimeTimePart(String value, char separator) {
        final int separatorIndex = value.indexOf(separator);
        if (separatorIndex == -1) {
            return "";
        } else {
            return value.substring(separatorIndex + 1).trim();
        }
    }

    private String parse(Perl5MatchProcessor matcher, ParsePattern[] patterns, String value) {
        for (int i = 0; i < patterns.length; i++) {
            final ParsePattern currentPattern = patterns[i];
            final MatchProcessor.Result result = matcher.match(currentPattern.getRe(), value);
            if (result.matches) {
                // Pattern matches
                return currentPattern.handle(result);
            }
        }

        // Return value unmodified
        return value;
    }

    private interface ParsePattern {
        public String getRe();
        public String handle(MatchProcessor.Result result);
    }

    // See also patterns on xforms.js
    private static ParsePattern[] DATE_PARSE_PATTERNS = new ParsePattern[] {
            // mm/dd/yyyy (American style)
            new ParsePattern() {
                public String getRe() {
                    return "^(\\d{1,2})\\/(\\d{1,2})\\/(\\d{2,4})$";
                }
                public String handle(MatchProcessor.Result result) {

                    final String year = (String) result.groups.get(2);
                    final String month = (String) result.groups.get(0);
                    final String day = (String) result.groups.get(1);
                    // TODO: year on 2 or 3 digits
                    final DateValue value = new DateValue(Integer.parseInt(year), Byte.parseByte(month), Byte.parseByte(day));
                    return value.getStringValue();
                }
            },
            // mm/dd (American style without year)
            new ParsePattern() {
                public String getRe() {
                    return "^(\\d{1,2})\\/(\\d{1,2})$";
                }
                public String handle(MatchProcessor.Result result) {

                    final String year = Integer.toString(new GregorianCalendar().get(Calendar.YEAR));// current year
                    final String month = (String) result.groups.get(0);
                    final String day = (String) result.groups.get(1);
                    final DateValue value = new DateValue(Integer.parseInt(year), Byte.parseByte(month), Byte.parseByte(day));
                    return value.getStringValue();
                }
            },
            // dd.mm.yyyy (Swiss style)
            new ParsePattern() {
                public String getRe() {
                    return "^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2,4})$";
                }
                public String handle(MatchProcessor.Result result) {

                    final String year = (String) result.groups.get(2);
                    final String month = (String) result.groups.get(1);
                    final String day = (String) result.groups.get(0);
                    // TODO: year on 2 or 3 digits
                    final DateValue value = new DateValue(Integer.parseInt(year), Byte.parseByte(month), Byte.parseByte(day));
                    return value.getStringValue();
                }
            },
            // yyyy-mm-dd (ISO style)
            new ParsePattern() {
                public String getRe() {
                    return "(\\d{2,4})-(\\d{1,2})-(\\d{1,2})";
                }
                public String handle(MatchProcessor.Result result) {

                    final String year = (String) result.groups.get(0);
                    final String month = (String) result.groups.get(1);
                    final String day = (String) result.groups.get(2);
                    // TODO: year on 2 or 3 digits
                    final DateValue value = new DateValue(Integer.parseInt(year), Byte.parseByte(month), Byte.parseByte(day));
                    return value.getStringValue();
                }
            }
    };

    // See also patterns on xforms.js
    private static ParsePattern[] TIME_PARSE_PATTERNS = new ParsePattern[] {
            // hh:mm:ss
            new ParsePattern() {
                public String getRe() {
                    return "(\\d{1,2}):(\\d{1,2}):(\\d{1,2})";
                }
                public String handle(MatchProcessor.Result result) {

                    final String hours = (String) result.groups.get(0);
                    final String minutes = (String) result.groups.get(1);
                    final String seconds = (String) result.groups.get(2);
                    final TimeValue value = new TimeValue(Byte.parseByte(hours), Byte.parseByte(minutes), Byte.parseByte(seconds), 0, CalendarValue.NO_TIMEZONE);
                    return value.getStringValue();
                }
            },
            // hh:mm
            new ParsePattern() {
                public String getRe() {
                    return "(\\d{1,2}):(\\d{1,2})";
                }
                public String handle(MatchProcessor.Result result) {

                    final String hours = (String) result.groups.get(0);
                    final String minutes = (String) result.groups.get(1);
                    final TimeValue value = new TimeValue(Byte.parseByte(hours), Byte.parseByte(minutes), (byte) 0, 0, CalendarValue.NO_TIMEZONE);
                    return value.getStringValue();
                }
            }
    };

    /**
     * Convenience method for handler: return the value of the first input field.
     *
     * @param pipelineContext   pipeline context
     * @return                  value to store in the first input field
     */
    public String getFirstValueUseFormat(PipelineContext pipelineContext) {
        final String result;

        final String typeName = getBuiltinTypeName();
        if ("date".equals(typeName) || "time".equals(typeName)) {
            // Format value specially
            result = formatSubValue(pipelineContext, getFirstValueType(), getValue(pipelineContext));
        } else if ("dateTime".equals(typeName)) {
            // Format value specially
            // Extract date part
            final String datePart = getDateTimeDatePart(getValue(pipelineContext), 'T');
            result = formatSubValue(pipelineContext, getFirstValueType(), datePart);
        } else {
            // Regular case, use external value
            result = getExternalValue(pipelineContext);
        }

        return (result != null) ? result : "";
    }

    /**
     * Convenience method for handler: return the value of the second input field.
     *
     * @param pipelineContext   pipeline context
     * @return                  value to store in the second input field
     */
    public String getSecondValueUseFormat(PipelineContext pipelineContext) {
        final String result;

        final String typeName = getBuiltinTypeName();
        if ("dateTime".equals(typeName)) {
            // Format value specially
            // Extract time part
            final String timePart = getDateTimeTimePart(getValue(pipelineContext), 'T');
            result = formatSubValue(pipelineContext, getSecondValueType(), timePart);
        } else {
            // N/A
            result = null;
        }

        return (result != null) ? result : "";
    }

    /**
     * Convenience method for handler: return a formatted value for read-only output.
     *
     * @param pipelineContext   pipeline context
     * @return                  formatted value
     */
    public String getReadonlyValueUseFormat(PipelineContext pipelineContext) {
        return getValueUseFormat(pipelineContext, getControlElement().attributeValue(new QName("format", XFormsConstants.XXFORMS_NAMESPACE)));
    }

    private String formatSubValue(PipelineContext pipelineContext, String valueType, String value) {
        // Assume xs: prefix for default formats
        final Map prefixToURIMap = new HashMap();
        prefixToURIMap.put(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);

        final Map variables = new HashMap();
        variables.put("v", value);

        final NodeInfo boundNode = getBoundNode();
        if (boundNode == null) {
            // No need to format
            return null;
        } else {
            // Format

            // Need to ensure the binding on the context stack is correct before evaluating XPath expressions
            getContextStack().setBinding(this);

            final String xpathExpression =
                    "if ($v castable as xs:" + valueType + ") then format-" + valueType + "(xs:" + valueType + "($v), '"
                            + XFormsProperties.getTypeInputFormat(containingDocument, valueType)
                            + "', 'en', (), ()) else $v";

            return XPathCache.evaluateAsString(pipelineContext, boundNode,
                    xpathExpression,
                    prefixToURIMap, variables,
                    XFormsContainingDocument.getFunctionLibrary(),
                    getContextStack().getFunctionContext(), null, getLocationData());
        }
    }

    public String getFirstValueType() {
        final String typeName = getBuiltinTypeName();
        if ("dateTime".equals(typeName)) {
            return "date";
        } else {
            return typeName;
        }
    }

    public String getSecondValueType() {
        final String typeName = getBuiltinTypeName();
        if ("dateTime".equals(typeName)) {
            return "time";
        } else {
            return null;
        }
    }

    public boolean addAttributesDiffs(PipelineContext pipelineContext, XFormsSingleNodeControl other, AttributesImpl attributesImpl, boolean isNewRepeatIteration) {
        final XFormsInputControl inputControlInfo1 = (XFormsInputControl) other;
        final XFormsInputControl inputControlInfo2 = this;

        boolean added = false;
//        for (int i = 0; i < XXFORMS_ATTRIBUTES.length; i++) {
//            final String xxformsAttribute = XXFORMS_ATTRIBUTES[i];
//
//            final String value1 = (inputControlInfo1 == null) ? null : inputControlInfo1.getExtensionAttributeValue(pipelineContext, xxformsAttribute);
//            final String value2 = inputControlInfo2.getExtensionAttributeValue(pipelineContext, xxformsAttribute);
//
//            if (!XFormsUtils.compareStrings(value1, value2)) {
//                final String attributeValue = value2 != null ? value2 : "";
//                added |= addAttributeIfNeeded(attributesImpl, xxformsAttribute, attributeValue, isNewRepeatIteration, attributeValue.equals(""));
//            }
//        }

        {
            // size
            final String sizeValue1 = (inputControlInfo1 == null) ? null : inputControlInfo1.getSize(pipelineContext);
            final String sizeValue2 = inputControlInfo2.getSize(pipelineContext);

            if (!XFormsUtils.compareStrings(sizeValue1, sizeValue2)) {
                final String attributeValue = sizeValue2 != null ? sizeValue2 : "";
                added |= addAttributeIfNeeded(attributesImpl, "size", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
            }
        }
        {
            // maxlength
            final String maxlengthValue1 = (inputControlInfo1 == null) ? null : inputControlInfo1.getMaxlength(pipelineContext);
            final String maxlengthValue2 = inputControlInfo2.getMaxlength(pipelineContext);

            if (!XFormsUtils.compareStrings(maxlengthValue1, maxlengthValue2)) {
                final String attributeValue = maxlengthValue2 != null ? maxlengthValue2 : "";
                added |= addAttributeIfNeeded(attributesImpl, "maxlength", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
            }
        }
        {
            // autocomplete
            final String autocompleteValue1 = (inputControlInfo1 == null) ? null : inputControlInfo1.getAutocomplete(pipelineContext);
            final String autocompleteValue2 = inputControlInfo2.getAutocomplete(pipelineContext);

            if (!XFormsUtils.compareStrings(autocompleteValue1, autocompleteValue2)) {
                final String attributeValue = autocompleteValue2 != null ? autocompleteValue2 : "";
                added |= addAttributeIfNeeded(attributesImpl, "autocomplete", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
            }
        }

        return added;
    }

    public boolean equalsExternal(PipelineContext pipelineContext, XFormsControl obj) {
        if (obj == null || !(obj instanceof XFormsInputControl))
            return false;

        if (this == obj)
            return true;

        final XFormsInputControl other = (XFormsInputControl) obj;

        if (!XFormsUtils.compareStrings(getSize(pipelineContext), other.getSize(pipelineContext)))
            return false;
        if (!XFormsUtils.compareStrings(getMaxlength(pipelineContext), other.getMaxlength(pipelineContext)))
            return false;
        if (!XFormsUtils.compareStrings(getAutocomplete(pipelineContext), other.getAutocomplete(pipelineContext)))
            return false;

        return super.equalsExternal(pipelineContext, obj);
    }
}
