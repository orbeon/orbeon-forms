/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.model

import org.orbeon.msv.grammar.Grammar
import org.orbeon.msv.reader.util.GrammarLoader
import org.orbeon.msv.reader.xmlschema.XMLSchemaReader
import org.orbeon.oxf.cache.ObjectCache
import org.orbeon.oxf.common.OrbeonLocationException
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.schema.{MSVGrammarReaderController, SchemaDependencies, SchemaInfo, SchemaKey}
import org.orbeon.oxf.xml.{ParserConfiguration, XMLParsing}
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData

import scala.util.control.NonFatal


trait XFormsModelSchemaValidatorPlatform {

  protected def loadCacheGrammar(containingDocument: XFormsContainingDocument, absoluteSchemaURL: String): Grammar = {
    try {
      val url = URLFactory.createURL(absoluteSchemaURL)
      val modificationTime = NetUtils.getLastModified(url)

      val cache = ObjectCache.instance
      val schemaKey = SchemaKey(absoluteSchemaURL)

      val schemaInfo = Option(cache.findValid(schemaKey, modificationTime)).map(_.asInstanceOf[SchemaInfo]).orNull

      if (schemaInfo == null || !schemaInfo.dependencies.areIncludesUnchanged) {
        val is = XMLParsing.EntityResolver.resolveEntity("", absoluteSchemaURL)
        val dependencies = new SchemaDependencies
        val controller = new MSVGrammarReaderController(containingDocument, dependencies, Some(absoluteSchemaURL))
        val factory = XMLParsing.getSAXParserFactory(ParserConfiguration.XIncludeOnly)

        val grammar = GrammarLoader.loadSchema(is, controller, factory)
        cache.add(schemaKey, modificationTime, SchemaInfo(grammar, dependencies))
        grammar
      } else {
        schemaInfo.grammar
      }
    } catch {
      case NonFatal(e) =>
        throw OrbeonLocationException.wrapException(e, XmlExtendedLocationData(absoluteSchemaURL, -1, -1, "loading schema from URI"))
    }
  }

  protected def newXMLSchemaReader(containingDocument: XFormsContainingDocument): XMLSchemaReader = {
    val dependencies = new SchemaDependencies
    val controller = new MSVGrammarReaderController(containingDocument, dependencies, None)
    val saxParserFactory = XMLParsing.getSAXParserFactory(ParserConfiguration.Plain)
    new XMLSchemaReader(controller, saxParserFactory)
  }
}
