/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function

import org.orbeon.oxf.xforms.XFormsObject
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.saxon.expr.{ExpressionTool, Expression, XPathContext}
import org.orbeon.saxon.om._
import org.orbeon.saxon.value.{BooleanValue, StringValue}
import collection.JavaConverters._

protected trait FunctionSupport extends XFormsFunction {

    import XFormsFunction._

    def stringArgument(i: Int)(implicit xpathContext: XPathContext) =
        arguments(i).evaluateAsString(xpathContext).toString

    def stringArgumentOpt(i: Int)(implicit xpathContext: XPathContext) =
        arguments.lift(i) map (_.evaluateAsString(xpathContext).toString)

    def stringArgumentOrContextOpt(i: Int)(implicit xpathContext: XPathContext) =
        stringArgumentOpt(i) orElse (Option(xpathContext.getContextItem) map (_.getStringValue))

    def booleanArgument(i: Int, default: Boolean)(implicit xpathContext: XPathContext) =
        arguments.lift(i) map effectiveBooleanValue getOrElse default

    def booleanArgumentOpt(i: Int)(implicit xpathContext: XPathContext) =
        arguments.lift(i) map effectiveBooleanValue

    def itemArgumentsOpt(i: Int)(implicit xpathContext: XPathContext) =
        arguments.lift(i) map (_.iterate(xpathContext))

    def itemArgumentOpt(i: Int)(implicit xpathContext: XPathContext) =
        itemArgumentsOpt(i) map (_.next())

    def itemArgumentOrContextOpt(i: Int)(implicit xpathContext: XPathContext) =
        Option(itemArgumentOpt(i) getOrElse xpathContext.getContextItem)

    def itemArgumentsOrContextOpt(i: Int)(implicit xpathContext: XPathContext) =
        itemArgumentsOpt(i) getOrElse SingletonIterator.makeIterator(xpathContext.getContextItem)

    // Resolve the relevant control by argument expression
    def relevantControl(i: Int)(implicit xpathContext: XPathContext): Option[XFormsControl] =
        relevantControl(arguments(i).evaluateAsString(xpathContext).toString)

    // Resolve a relevant control by id
    def relevantControl(staticOrAbsoluteId: String)(implicit xpathContext: XPathContext): Option[XFormsControl] =
        resolveOrFindByStaticOrAbsoluteId(staticOrAbsoluteId) collect
            { case control: XFormsControl if control.isRelevant ⇒ control }

    // Resolve an object by id
    def resolveOrFindByStaticOrAbsoluteId(staticOrAbsoluteId: String)(implicit xpathContext: XPathContext): Option[XFormsObject] =
        Option(context.container.resolveObjectByIdInScope(getSourceEffectiveId, staticOrAbsoluteId, null))

    def resolveStaticOrAbsoluteId(staticIdExpr: Option[Expression])(implicit xpathContext: XPathContext): Option[String] =
        staticIdExpr match {
            case None ⇒
                // If no argument is supplied, return the closest id (source id)
                Option(getSourceEffectiveId)
            case Some(expr) ⇒
                // Otherwise resolve the id passed against the source id
                val staticOrAbsoluteId = expr.evaluateAsString(xpathContext).toString
                Option(context.container.resolveObjectByIdInScope(getSourceEffectiveId, staticOrAbsoluteId, null)) map
                    (_.getEffectiveId)
        }

    def effectiveBooleanValue(e: Expression)(implicit xpathContext: XPathContext) =
        ExpressionTool.effectiveBooleanValue(e.iterate(xpathContext))

    def asIterator(v: Array[String]) = new ArrayIterator(v map StringValue.makeStringValue)
    def asIterator(v: Seq[String])   = new ListIterator (v map StringValue.makeStringValue asJava)
    def asItem(v: Option[String])    = v map stringToStringValue orNull

    implicit def itemSeqOptToIterator(v: Option[Seq[Item]])     = v map (s ⇒ new ListIterator(s.asJava)) getOrElse EmptyIterator.getInstance
    implicit def stringSeqOptToIterator(v: Option[Seq[String]]) = v map asIterator getOrElse EmptyIterator.getInstance

    implicit def stringToStringValue(v: String)     = StringValue.makeStringValue(v)
    implicit def booleanToBooleanValue(v: Boolean)  = BooleanValue.get(v)
    implicit def stringOptToItem(v: Option[String]) = asItem(v)
}
