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
package org.orbeon.oxf.xforms.function.xxforms

import java.text.MessageFormat
import org.orbeon.oxf.xforms.function.{FunctionSupport, XFormsFunction}
import org.orbeon.saxon.Configuration
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.StringValue
import org.orbeon.saxon.value.Value
import org.orbeon.scaxon.XML._

class XXFormsFormatMessage extends XFormsFunction with FunctionSupport {

    override def evaluateItem(xpathContext: XPathContext): StringValue = {

        implicit val ctx = xpathContext

        val template         = stringArgument(0)
        val messageArguments = argument(1).iterate(xpathContext)

        // Convert sequence to array of Java objects
        val arguments = asScalaIterator(messageArguments) map Value.convertToJava toArray

        // Find xml:lang and set locale if any

        val format =
            elementAnalysisForSource(xpathContext) flatMap (XXFormsLang.resolveXMLangHandleAVTs(getContainingDocument(xpathContext), _)) match {
                case Some(lang) ⇒
                    // Really not sure how xml:lang should be parsed, see:
                    //
                    // http://www.w3.org/International/articles/language-tags/
                    // http://sites.google.com/site/openjdklocale/design-specification
                    // IETF BCP 47: http://www.rfc-editor.org/rfc/bcp/bcp47.txt

                    // Use Saxon utility for now
                    new MessageFormat(template, Configuration.getLocale(lang))
                case None ⇒
                    new MessageFormat(template)
            }

        format.format(arguments)
    }
}