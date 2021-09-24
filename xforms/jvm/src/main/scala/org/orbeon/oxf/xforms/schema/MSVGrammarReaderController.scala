/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.schema

import java.net.URL

import org.orbeon.msv.grammar.Grammar
import org.orbeon.msv.reader.GrammarReaderController
import org.orbeon.oxf.cache.CacheKey
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.processor.validation.SchemaValidationException
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms.model.XFormsModelSchemaValidator
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsUtils}
import org.orbeon.oxf.xml.XMLParsing
import org.orbeon.oxf.xml.dom.XmlLocationData
import org.xml.sax.{InputSource, Locator}

import scala.util.control.NonFatal

case class SchemaInfo(grammar: Grammar, dependencies: SchemaDependencies)
case class SchemaKey(urlString: String) extends CacheKey

class MSVGrammarReaderController(
    containingDocument: XFormsContainingDocument,
    dependencies      : SchemaDependencies,
    baseURL           : Option[String]
  ) extends GrammarReaderController {

  def resolveEntity(publicId: String, systemId: String): InputSource = {

    // The base URL is specified if the top-level schema is not inline. Imports resolve against the location of that
    // base URL. If the base URL is not specified, the top-level schema is inline, and we resolve the imports as
    // service URLs, as we do for a top-level schema and instances.
    val url =
      baseURL match {
        case Some(baseURL) =>
          URLFactory.createURL(baseURL, systemId)
        case None =>
          URLFactory.createURL(XFormsUtils.resolveServiceURL(containingDocument, null, systemId, URLRewriter.REWRITE_MODE_ABSOLUTE))
      }

    dependencies.addInclude(url)
    XMLParsing.ENTITY_RESOLVER.resolveEntity("", url.toString)
  }

  def warning(locators: Array[Locator], message: String): Unit = {

    def locToString(loc: Locator) =
      loc.getSystemId + ", line " + loc.getLineNumber + ", column " + loc.getColumnNumber

    val formatted =
      locators match {
        case Array()  => message
        case locators => s"${locators map locToString mkString ", "}: $message"
      }

    XFormsModelSchemaValidator.logger.warn(formatted)
  }

  def error(locators: Array[Locator], message: String, exception: Exception): Unit = {
    val locationData = locators.headOption map XmlLocationData.createIfPresent
    throw new SchemaValidationException(message, exception, locationData.orNull)
  }
}

class SchemaDependencies {

  private var includes: List[(URL, Long)] = Nil

  def addInclude(url: URL): Unit =
    includes ::= url -> NetUtils.getLastModified(url)

  def areIncludesUnchanged: Boolean = {

    def isUnchanged(url: URL, last: Long) =
      try
        NetUtils.getLastModified(url) == last
      catch {
        // If an include is missing it may just be the case that it isn't included anymore _and_ it has been
        // removed. So, we return `false` and then on a reparse we will find out the truth.
        case NonFatal(e) => false
      }

    includes forall (isUnchanged _).tupled
  }
}
