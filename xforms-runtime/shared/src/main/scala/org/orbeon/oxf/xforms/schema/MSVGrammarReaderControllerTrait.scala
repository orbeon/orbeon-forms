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

import org.orbeon.msv.reader.GrammarReaderController
import org.orbeon.oxf.processor.validation.SchemaValidationException
import org.orbeon.oxf.xforms.model.XFormsModelSchemaValidator
import org.orbeon.oxf.xml.dom.XmlLocationData
import org.xml.sax.Locator


trait MSVGrammarReaderControllerTrait extends GrammarReaderController {

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
