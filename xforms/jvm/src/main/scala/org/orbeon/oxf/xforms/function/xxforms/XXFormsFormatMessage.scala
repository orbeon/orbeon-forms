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

import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.Configuration
import org.orbeon.saxon.expr.{PathMap, XPathContext}
import org.orbeon.saxon.value.{StringValue, Value}
import org.orbeon.scaxon.Implicits._

class XXFormsFormatMessage extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): StringValue = {

    implicit val ctx = xpathContext

    val template         = stringArgument(0)
    val messageArguments = argument(1).iterate(xpathContext)

    // Convert sequence to array of Java objects
    val arguments = asScalaIterator(messageArguments) map Value.convertToJava toArray

    // Find xml:lang and set locale if any

    val format =
      elementAnalysisForSource flatMap (XXFormsLang.resolveXMLangHandleAVTs(getContainingDocument, _)) match {
        case Some(lang) ⇒
          // Not sure how xml:lang should be parsed, see:
          //
          // XML spec points to:
          //
          // - http://tools.ietf.org/html/rfc4646
          // - http://tools.ietf.org/html/rfc4647
          //
          // NOTES:
          //
          // - IETF BCP 47 replaces RFC 4646 (and includes RFC 5646 and RFC 4647)
          // - Java 7 has an improved Locale class which supports parsing BCP 47
          //
          // http://docs.oracle.com/javase/7/docs/api/java/util/Locale.html#forLanguageTag(java.lang.String)
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

  override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet = {
    XXFormsLang.addXMLLangDependency(pathMap)
    arguments foreach (_.addToPathMap(pathMap, pathMapNodeSet))
    null
  }
}