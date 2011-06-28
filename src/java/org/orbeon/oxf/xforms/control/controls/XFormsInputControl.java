/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.processor.MatchProcessor;
import org.orbeon.oxf.processor.Perl5MatchProcessor;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.ValueRepresentation;
import org.orbeon.saxon.value.*;
import org.orbeon.saxon.value.StringValue;

import java.util.*;

/**
 * Represents an xforms:input control.
 */
public class XFormsInputControl extends XFormsValueControl {

    // List of attributes to handle as AVTs
    private static final QName[] EXTENSION_ATTRIBUTES = {
            XFormsConstants.XXFORMS_SIZE_QNAME,
            XFormsConstants.XXFORMS_MAXLENGTH_QNAME,
            XFormsConstants.XXFORMS_AUTOCOMPLETE_QNAME
    };

    // Optional display format
    private final String format;
    private final String unformat;

    public XFormsInputControl(XBLContainer container, XFormsControl parent, Element element, String name, String id, Map<String, Element> state) {
        super(container, parent, element, name, id);
        if (element != null) { // can be null in some unit tests only
            this.format = element.attributeValue(new QName("format", XFormsConstants.XXFORMS_NAMESPACE));
            this.unformat = element.attributeValue(new QName("unformat", XFormsConstants.XXFORMS_NAMESPACE));
        } else {
            this.format = this.unformat = null;
        }
    }

    @Override
    protected QName[] getExtensionAttributes() {
        return EXTENSION_ATTRIBUTES;
    }

    public String getSize() {
        return getExtensionAttributeValue(XFormsConstants.XXFORMS_SIZE_QNAME);
    }

    public String getMaxlength() {
        return getExtensionAttributeValue(XFormsConstants.XXFORMS_MAXLENGTH_QNAME);
    }

    public String getAutocomplete() {
        return getExtensionAttributeValue(XFormsConstants.XXFORMS_AUTOCOMPLETE_QNAME);
    }

    @Override
    protected void evaluateExternalValue() {

        assert isRelevant();

        final String internalValue = getValue();
        assert internalValue != null;

        final String updatedValue;

        final String typeName = getBuiltinTypeName();
        if (typeName != null) {
            if (typeName.equals("boolean")) {
                // xs:boolean

                // NOTE: We have decided that it did not make much sense to encrypt the value for boolean. This also poses
                // a problem since the server does not send an itemset for new booleans, therefore the client cannot know
                // the encrypted value of "true". So we do not encrypt values.

                updatedValue = Boolean.toString("true".equals(internalValue));
            } else {
                // Other types
                // For now, format only if the format attribute is present
                updatedValue = (format != null) ?  getValueUseFormat(format) : internalValue;
            }
        } else {
            // No type, format if the format attribute is present
            updatedValue = (format != null) ?  getValueUseFormat(format) : internalValue;
        }

        setExternalValue(updatedValue);
    }

    @Override
    public void storeExternalValue(String value, String type) {
        // Store after converting
        super.storeExternalValue(convertFromExternalValue(value), type);

        // Tricky: mark the external value as dirty if there is a format, as the client will expect an up to date formatted value
        if (format != null) {
            markExternalValueDirty();
            containingDocument.getControls().markDirtySinceLastRequest(false);
        }
    }

    private String convertFromExternalValue(String externalValue) {
        final String typeName = getBuiltinTypeName();
        if (typeName != null) {
            if (typeName.equals("boolean")) {
                // Boolean input

                // NOTE: We have decided that it did not make much sense to encrypt the value for boolean. This also poses
                // a problem since the server does not send an itemset for new booleans, therefore the client cannot know
                // the encrypted value of "true". So we do not encrypt values.

                // Anything but "true" is "false"
                if (!externalValue.equals("true"))
                    externalValue = "false";
            } else if (containingDocument.getStaticState().isNoscript()) {
                // Noscript mode: value must be pre-processed on the server (in Ajax mode, ISO value is sent to server if possible)

                if ( "date".equals(typeName)) {
                    // Date input
                    externalValue = externalValue.trim();
                    final Perl5MatchProcessor matcher = new Perl5MatchProcessor();
                    // TODO: like on client, must handle oxf.xforms.format.input.date
                    externalValue = parse(matcher, DATE_PARSE_PATTERNS, externalValue);
                } else if ("time".equals(typeName)) {
                    // Time input
                    externalValue = externalValue.trim();
                    final Perl5MatchProcessor matcher = new Perl5MatchProcessor();
                    // TODO: like on client, must handle oxf.xforms.format.input.time
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
                        externalValue = "";
                    } else {
                        // Parse and recombine with 'T' separator (result may be invalid dateTime, of course!)
                        final Perl5MatchProcessor matcher = new Perl5MatchProcessor();
                        externalValue = parse(matcher, DATE_PARSE_PATTERNS, datePart) + 'T' + parse(matcher, TIME_PARSE_PATTERNS, timePart);
                    }
                } else {
                    externalValue = convertFromExternalValueUseUnformat(externalValue);
                }
            } else {
                externalValue = convertFromExternalValueUseUnformat(externalValue);
            }
        } else {
            externalValue = convertFromExternalValueUseUnformat(externalValue);
        }

        return externalValue;
    }

    private String convertFromExternalValueUseUnformat(String externalValue) {
        if (unformat != null) {
            final String result = evaluateAsString(unformat, Collections.<Item>singletonList(StringValue.makeStringValue(externalValue)), 1);
            return (result != null) ? result : externalValue;
        } else {
            return externalValue;
        }
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

    private static String parse(Perl5MatchProcessor matcher, ParsePattern[] patterns, String value) {
        for (final ParsePattern currentPattern: patterns) {
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
            // TODO: remaining regexps from xforms.js?
            // Today
            // Tomorrow
            // Yesterday
            // 4th
            // 4th Jan
            // 4th Jan 2003
            // Jan 4th
            // Jan 4th 2003
            // next Tuesday - this is suspect due to weird meaning of "next"
            // last Tuesday

            // mm/dd/yyyy (American style)
            new ParsePattern() {
                public String getRe() {
                    return "^(\\d{1,2})\\/(\\d{1,2})\\/(\\d{2,4})$";
                }
                public String handle(MatchProcessor.Result result) {

                    final String year = result.groups.get(2);
                    final String month = result.groups.get(0);
                    final String day = result.groups.get(1);
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
                    final String month = result.groups.get(0);
                    final String day = result.groups.get(1);
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

                    final String year = result.groups.get(2);
                    final String month = result.groups.get(1);
                    final String day = result.groups.get(0);
                    // TODO: year on 2 or 3 digits
                    final DateValue value = new DateValue(Integer.parseInt(year), Byte.parseByte(month), Byte.parseByte(day));
                    return value.getStringValue();
                }
            },
            // yyyy-mm-dd (ISO style)
            new ParsePattern() {
                public String getRe() {
                    return "(\\d{2,4})-(\\d{1,2})-(\\d{1,2})(Z|([+-]\\d{2}:\\d{2}))?";
                }
                public String handle(MatchProcessor.Result result) {

                    final String year = result.groups.get(0);
                    final String month = result.groups.get(1);
                    final String day = result.groups.get(2);
                    // TODO: year on 2 or 3 digits
                    final DateValue value = new DateValue(Integer.parseInt(year), Byte.parseByte(month), Byte.parseByte(day));
                    return value.getStringValue();
                }
            }
    };

    // See also patterns on xforms.js
    private static ParsePattern[] TIME_PARSE_PATTERNS = new ParsePattern[] {
            // TODO: remaining regexps from xforms.js?
            // Now
            // TODO

            // 12:34:56 p.m.
            new ParsePattern() {
                public String getRe() {
                    return "^(\\d{1,2}):(\\d{1,2}):(\\d{1,2}) ?(p|pm|p\\.\\m\\.)$";
                }
                public String handle(MatchProcessor.Result result) {
                    byte hoursByte = Byte.parseByte(result.groups.get(0));
                    if (hoursByte < 12) hoursByte += 12;

                    final String minutes = result.groups.get(1);
                    final String seconds = result.groups.get(2);
                    final TimeValue value = new TimeValue(hoursByte, Byte.parseByte(minutes), Byte.parseByte(seconds), 0, CalendarValue.NO_TIMEZONE);
                    return value.getStringValue();
                }
            },
            // 12:34 p.m.
            new ParsePattern() {
                public String getRe() {
                    return "^(\\d{1,2}):(\\d{1,2}) ?(p|pm|p\\.\\m\\.)$";
                }
                public String handle(MatchProcessor.Result result) {
                    byte hoursByte = Byte.parseByte(result.groups.get(0));
                    if (hoursByte < 12) hoursByte += 12;

                    final String minutes = result.groups.get(1);
                    final TimeValue value = new TimeValue(hoursByte, Byte.parseByte(minutes), (byte) 0, 0, CalendarValue.NO_TIMEZONE);
                    return value.getStringValue();
                }
            },
            // 12 p.m.
            new ParsePattern() {
                public String getRe() {
                    return "^(\\d{1,2}) ?(p|pm|p\\.\\m\\.)$";
                }
                public String handle(MatchProcessor.Result result) {
                    byte hoursByte = Byte.parseByte(result.groups.get(0));
                    if (hoursByte < 12) hoursByte += 12;

                    final TimeValue value = new TimeValue(hoursByte, (byte) 0, (byte) 0, 0, CalendarValue.NO_TIMEZONE);
                    return value.getStringValue();
                }
            },
            // 12:34:56 (a.m.)
            new ParsePattern() {
                public String getRe() {
                    return "^(\\d{1,2}):(\\d{1,2}):(\\d{1,2}) ?(a|am|a\\.\\m\\.)?$";
                }
                public String handle(MatchProcessor.Result result) {
                    final String hours = result.groups.get(0);
                    final String minutes = result.groups.get(1);
                    final String seconds = result.groups.get(2);
                    final TimeValue value = new TimeValue(Byte.parseByte(hours), Byte.parseByte(minutes), Byte.parseByte(seconds), 0, CalendarValue.NO_TIMEZONE);
                    return value.getStringValue();
                }
            },
            // 12:34 (a.m.)
            new ParsePattern() {
                public String getRe() {
                    return "^(\\d{1,2}):(\\d{1,2}) ?(a|am|a\\.\\m\\.)?$";
                }
                public String handle(MatchProcessor.Result result) {

                    final String hours = result.groups.get(0);
                    final String minutes = result.groups.get(1);
                    final TimeValue value = new TimeValue(Byte.parseByte(hours), Byte.parseByte(minutes), (byte) 0, 0, CalendarValue.NO_TIMEZONE);
                    return value.getStringValue();
                }
            },
            // 12 (a.m.)
            new ParsePattern() {
                public String getRe() {
                    return "^(\\d{1,2}) ?(a|am|a\\.\\m\\.)?$";
                }
                public String handle(MatchProcessor.Result result) {

                    final String hours = result.groups.get(0);
                    final TimeValue value = new TimeValue(Byte.parseByte(hours), (byte) 0, (byte) 0, 0, CalendarValue.NO_TIMEZONE);
                    return value.getStringValue();
                }
            },
            // hhmmss
            /* TODO: JS code has this, need to implement same logic
            new ParsePattern() {
                public String getRe() {
                    return "^(\\d{1,6})$";
                }
                public String handle(MatchProcessor.Result result) {

                    final String all = result.groups.get(0);

//                    var d = new Date();
//                    var h = bits[1].substring(0,2);
//                    var m = parseInt(bits[1].substring(2,4), 10);
//                    var s = parseInt(bits[1].substring(4,6), 10);
//                    if (isNaN(m)) {m = 0;}
//                    if (isNaN(s)) {s = 0;}
//                    d.setHours(parseInt(h, 10));
//                    d.setMinutes(parseInt(m, 10));
//                    d.setSeconds(parseInt(s, 10));


                    final String minutes = result.groups.get(1);
                    final TimeValue value = new TimeValue(Byte.parseByte(hours), Byte.parseByte(minutes), (byte) 0, 0, CalendarValue.NO_TIMEZONE);
                    return value.getStringValue();
                }
            }
            */
    };

    /**
     * Convenience method for handler: return the value of the first input field.
     *
     * @return                  value to store in the first input field
     */
    public String getFirstValueUseFormat() {
        final String result;

        if (isRelevant()) {
            final String typeName = getBuiltinTypeName();
            if ("date".equals(typeName) || "time".equals(typeName)) {
                // Format value specially
                result = formatSubValue(getFirstValueType(), getValue());
            } else if ("dateTime".equals(typeName)) {
                // Format value specially
                // Extract date part
                final String datePart = getDateTimeDatePart(getValue(), 'T');
                result = formatSubValue(getFirstValueType(), datePart);
            } else {
                // Regular case, use external value
                result = getExternalValue();
            }
        } else {
            result = null;
        }

        return (result != null) ? result : "";
    }

    /**
     * Convenience method for handler: return the value of the second input field.
     *
     * @return                  value to store in the second input field
     */
    public String getSecondValueUseFormat() {
        final String result;

        if (isRelevant()) {
            final String typeName = getBuiltinTypeName();
            if ("dateTime".equals(typeName)) {
                // Format value specially
                // Extract time part
                final String timePart = getDateTimeTimePart(getValue(), 'T');
                result = formatSubValue(getSecondValueType(), timePart);
            } else {
                // N/A
                result = null;
            }
        } else {
            result = null;
        }

        return (result != null) ? result : "";
    }

    /**
     * Convenience method for handler: return a formatted value for read-only output.
     *
     * @return                  formatted value
     */
    public String getReadonlyValueUseFormat() {
        return isRelevant() ? getValueUseFormat(format) : null;
    }

    private String formatSubValue(String valueType, String value) {

        final Map<String, ValueRepresentation> variables = new HashMap<String, ValueRepresentation>();
        variables.put("v", new StringValue(value));

        final Item boundItem = getBoundItem();
        if (boundItem == null) {
            // No need to format
            return null;
        } else {
            // Format
            final String xpathExpression =
                    "if ($v castable as xs:" + valueType + ") then format-" + valueType + "(xs:" + valueType + "($v), '"
                            + XFormsProperties.getTypeInputFormat(containingDocument, valueType)
                            + "', 'en', (), ()) else $v";

            return evaluateAsString(boundItem, xpathExpression, FORMAT_NAMESPACE_MAPPING, variables);
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

    public static String testParseTime(String value) {
        final Perl5MatchProcessor matcher = new Perl5MatchProcessor();
        return parse(matcher, XFormsInputControl.TIME_PARSE_PATTERNS, value);
    }

    public static String testParseDate(String value) {
        final Perl5MatchProcessor matcher = new Perl5MatchProcessor();
        return parse(matcher, XFormsInputControl.DATE_PARSE_PATTERNS, value);
    }
}
