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
package org.orbeon.oxf.xforms.control.controls

import XFormsInputControl._
import collection.JavaConverters._
import java.util.{GregorianCalendar, Calendar}
import org.dom4j.Element
import org.orbeon.oxf.processor.RegexpMatcher.MatchResult
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.om.ValueRepresentation
import org.orbeon.saxon.value.CalendarValue
import org.orbeon.saxon.value.DateValue
import org.orbeon.saxon.value.StringValue
import org.orbeon.saxon.value.TimeValue
import scala.util.matching.Regex
import org.xml.sax.helpers.AttributesImpl
import org.orbeon.oxf.xml.XMLConstants._

/**
 * xf:input control
 */
class XFormsInputControl(container: XBLContainer, parent: XFormsControl, element: Element, id: String)
        extends XFormsSingleNodeControl(container, parent, element, id)
        with XFormsValueControl
        with FocusableTrait {

    private def format   = Option(staticControl) flatMap (_.format)
    private def unformat = Option(staticControl) flatMap (_.unformat)

    private def unformatTransform(v: String) = unformat match {
        case Some(expr) ⇒ evaluateAsString(expr, Seq(StringValue.makeStringValue(v)), 1) getOrElse ""
        case None       ⇒ v
    }

    // Control name becomes "label" or "hint". This is a special case as even non-external labels and hints can
    // have the placeholder appearance, and those are not really controls.
    override def getJavaScriptInitialization =
        Option(getPlaceholderInfo(staticControl, this)) map
            (p ⇒ (if (p.isLabelPlaceholder) "label" else "hint", Dom4jUtils.qNameToExplodedQName(XFORMS_MINIMAL_APPEARANCE_QNAME), getEffectiveId)) orNull

    override def evaluateExternalValue() : Unit = {
        assert(isRelevant)

        val internalValue = getValue
        assert(internalValue ne null)

        // TODO: format must take place between instance and internal value instead

        val typeName = getBuiltinTypeName
        val updatedValue =
            if (typeName == "boolean")
                // xs:boolean

                // NOTE: We have decided that it did not make much sense to encrypt the value for boolean. This also poses
                // a problem since the server does not send an itemset for new booleans, therefore the client cannot know
                // the encrypted value of "true". So we do not encrypt values.
                normalizeBooleanString(internalValue)
            else
                // Other types or no type
                // Format only if the format attribute is present. We don't use the default formats, because we don't
                // yet have default "unformats".
                format flatMap valueWithSpecifiedFormat getOrElse internalValue

        setExternalValue(updatedValue)
    }

    override def translateExternalValue(externalValue: String) = {

        // Tricky: mark the external value as dirty if there is a format, as the client will expect an up to date formatted value
        format foreach { _ ⇒
            markExternalValueDirty()
            containingDocument.getControls.markDirtySinceLastRequest(false)
        }

        // NOTE: We have decided that it did not make much sense to encrypt the value for boolean. This also poses
        // a problem since the server does not send an itemset for new booleans, therefore the client cannot know
        // the encrypted value of "true". So we do not encrypt values.

        val isNoscript = containingDocument.getStaticState.isNoscript

        getBuiltinTypeName match {
            case "boolean"                ⇒ normalizeBooleanString(externalValue)
            case "date"     if isNoscript ⇒ parse(DateParsePatterns, externalValue.trim)
            case "time"     if isNoscript ⇒ parse(TimeParsePatterns, externalValue.trim)
            case "dateTime" if isNoscript ⇒
                // Split into date and time parts
                // We use the same separator as the repeat separator. This is set in xforms-server-submit.xpl.
                val datePart = getDateTimeDatePart(externalValue.trim, REPEAT_HIERARCHY_SEPARATOR_1)
                val timePart = getDateTimeTimePart(externalValue.trim, REPEAT_HIERARCHY_SEPARATOR_1)
                if (datePart.nonEmpty || timePart.nonEmpty)
                    // Parse and recombine with 'T' separator (result may be invalid dateTime, of course!)
                    parse(DateParsePatterns, datePart) + 'T' + parse(TimeParsePatterns, timePart)
                else
                    // Special case of empty parts
                    ""
            case "string" | null ⇒
                // Replacement-based input sanitation for string type only
                containingDocument.getStaticState.sanitizeInput(unformatTransform(externalValue))
            case _ ⇒
                unformatTransform(externalValue)
        }
    }

    // Convenience method for handler: return the value of the first input field.
    def getFirstValueUseFormat = {
        val result =
            if (isRelevant) {
                getBuiltinTypeName match {
                    case "date" | "time" ⇒ formatSubValue(getFirstValueType, getValue)
                    case "dateTime"      ⇒ formatSubValue(getFirstValueType, getDateTimeDatePart(getValue, 'T'))
                    case _               ⇒ Option(getExternalValue)
                }
            } else
                None

        result getOrElse ""
    }

    // Convenience method for handler: return the value of the second input field.
    def getSecondValueUseFormat = {
        val result =
            if (isRelevant) {
                getBuiltinTypeName match {
                    case "dateTime"      ⇒ formatSubValue(getSecondValueType, getDateTimeTimePart(getValue, 'T'))
                    case _               ⇒ None
                }
            } else
                None

        result getOrElse ""
    }

    // Convenience method for handler: return a formatted value for read-only output
    def getReadonlyValue =
        getValueUseFormat(format) getOrElse getExternalValue

    private def formatSubValue(valueType: String, value: String) = {
        val variables = Map[String, ValueRepresentation]("v" → StringValue.makeStringValue(value))

        val boundItem = getBoundItem
        if (boundItem eq null)
            // No need to format
            null
        else {
            // Format
            val xpathExpression =
                "if ($v castable as xs:" +
                valueType +
                ") then format-" +
                valueType +
                "(xs:" +
                valueType +
                "($v), '" +
                XFormsProperties.getTypeInputFormat(containingDocument, valueType) +
                "', 'en', (), ()) else $v"

            evaluateAsString(xpathExpression, Option(boundItem), XFormsValueControl.FormatNamespaceMapping, variables.asJava)
        }
    }

    def getFirstValueType = {
        val typeName = getBuiltinTypeName
        if (typeName == "dateTime") "date" else typeName
    }

    def getSecondValueType =
        if (getBuiltinTypeName == "dateTime") "time" else null

    // Also compare type
    override def equalsExternal(other: XFormsControl) =
        other match {
            case other if this eq other ⇒ true
            case other: XFormsInputControl ⇒
                valueType == other.valueType &&
                super.equalsExternal(other)
            case _ ⇒ false
        }

    // Add type attribute if needed
    override def addAjaxAttributes(attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean, other: XFormsControl): Boolean = {

        var added = super.addAjaxAttributes(attributesImpl, isNewlyVisibleSubtree, other)

        val typeValue1 = if (isNewlyVisibleSubtree) null else other.asInstanceOf[XFormsInputControl].typeExplodedQName
        val typeValue2 = typeExplodedQName
        if (isNewlyVisibleSubtree || typeValue1 != typeValue2) {
            val attributeValue = if (typeValue2 ne null) typeValue2 else ""
            added |= AjaxSupport.addOrAppendToAttributeIfNeeded(attributesImpl, "type", attributeValue, isNewlyVisibleSubtree, attributeValue == "" || StringQNames(attributeValue))
        }

        added
    }
}

object XFormsInputControl {

    val StringQNames = Set(XS_STRING_EXPLODED_QNAME, XFORMS_STRING_EXPLODED_QNAME)
    
    // Anything but "true" is "false"
    private def normalizeBooleanString(s: String) = (s == "true").toString

    private def getDateTimeDatePart(value: String, separator: Char) = {
        val separatorIndex = value.indexOf(separator)
        if (separatorIndex == -1)
            value
        else
            value.substring(0, separatorIndex).trim
    }

    private def getDateTimeTimePart(value: String, separator: Char) = {
        val separatorIndex = value.indexOf(separator)
        if (separatorIndex == -1)
            ""
        else
            value.substring(separatorIndex + 1).trim
    }

    private def parse(patterns: Seq[ParsePattern], value: String): String = {
        for (currentPattern ← patterns.toIterator) {
            val result = MatchResult(currentPattern.regex.pattern, value)
            if (result.matches)
                return currentPattern(result)
        }
        value
    }

    def testParseTime(value: String) = parse(TimeParsePatterns, value)
    def testParseDate(value: String) = parse(XFormsInputControl.DateParsePatterns, value)

    def getPlaceholderInfo(elementAnalysis: ElementAnalysis, control: XFormsControl): PlaceHolderInfo = {
        val isLabelPlaceholder = LHHAAnalysis.hasLHHAPlaceholder(elementAnalysis, "label")
        val isHintPlaceholder = ! isLabelPlaceholder && LHHAAnalysis.hasLHHAPlaceholder(elementAnalysis, "hint")
        if (isLabelPlaceholder || isHintPlaceholder) {
            // null if no placeholder, "" if placeholder and non-concrete control, placeholder value otherwise
            val placeholderValue =
                if ((control ne null) && control.isRelevant) {
                    if (isLabelPlaceholder) control.getLabel else control.getHint
                } else
                    ""

            new XFormsInputControl.PlaceHolderInfo(isLabelPlaceholder, isHintPlaceholder, placeholderValue)
        } else
            null
    }

    private val DateParsePatterns = Array(
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
        new ParsePattern("^(\\d{1,2})\\/(\\d{1,2})\\/(\\d{2,4})$".r) {
            def apply(result: MatchResult) =
                stringsToDate(result.group(2), result.group(0), result.group(1))
        },
        // mm/dd (American style without year)
        new ParsePattern("^(\\d{1,2})\\/(\\d{1,2})$".r) {
            def apply(result: MatchResult) =
                stringsToDate((new GregorianCalendar).get(Calendar.YEAR).toString, result.group(0), result.group(1))
        },
        // dd.mm.yyyy (Swiss style)
        new ParsePattern("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2,4})$".r) {
            def apply(result: MatchResult) =
                stringsToDate(result.group(2), result.group(1), result.group(0))
        },
        // yyyy-mm-dd (ISO style)
        new ParsePattern("(\\d{2,4})-(\\d{1,2})-(\\d{1,2})(Z|([+-]\\d{2}:\\d{2}))?".r) {
            def apply(result: MatchResult) =
                stringsToDate(result.group(0), result.group(1), result.group(2))
        }
    )

    // See also patterns on xforms.js
    private val TimeParsePatterns = Array(
        // TODO: remaining regexps from xforms.js?

        // 12:34:56 p.m.
        new ParsePattern("^(\\d{1,2}):(\\d{1,2}):(\\d{1,2}) ?(p|pm|p\\.m\\.)$".r) {
            def apply(result: MatchResult) =
                stringsToTime(adjustHoursPM(result.group(0)), result.group(1), result.group(2))
        },
        // 12:34 p.m.
        new ParsePattern("^(\\d{1,2}):(\\d{1,2}) ?(p|pm|p\\.m\\.)$".r) {
            def apply(result: MatchResult) =
                stringsToTime(adjustHoursPM(result.group(0)), result.group(1), "0")
        },
        // 12 p.m.
        new ParsePattern("^(\\d{1,2}) ?(p|pm|p\\.m\\.)$".r) {
            def apply(result: MatchResult) =
                stringsToTime(adjustHoursPM(result.group(0)), "0", "0")
        },
        // 12:34:56 (a.m.)
        new ParsePattern("^(\\d{1,2}):(\\d{1,2}):(\\d{1,2}) ?(a|am|a\\.m\\.)?$".r) {
            def apply(result: MatchResult) =
                stringsToTime(result.group(0), result.group(1), result.group(2))
        },
        // 12:34 (a.m.)
        new ParsePattern("^(\\d{1,2}):(\\d{1,2}) ?(a|am|a\\.m\\.)?$".r) {
            def apply(result: MatchResult) =
                stringsToTime(result.group(0), result.group(1), "0")
        },
        // 12 (a.m.)
        new ParsePattern("^(\\d{1,2}) ?(a|am|a\\.m\\.)?$".r) {
            def apply(result: MatchResult) =
                stringsToTime(result.group(0), "0", "0")
        }
    )

    private abstract class ParsePattern(val regex: Regex) extends Function1[MatchResult, String] {
        def stringsToDate(year: String, month: String, day: String) =
            (new DateValue(year.toInt, month.toByte, day.toByte)).getStringValue

        def stringsToTime(hours: String, minutes: String, seconds: String) =
            (new TimeValue(hours.toByte, minutes.toByte, seconds.toByte, 0, CalendarValue.NO_TIMEZONE)).getStringValue

        def adjustHoursPM(hours: String) = {
            val hoursInt = hours.toInt
            if (hoursInt < 12)
                (hoursInt + 12).toString
            else
                hours
        }
    }

    case class PlaceHolderInfo(isLabelPlaceholder: Boolean, isHintPlaceholder: Boolean, placeholder: String)
}