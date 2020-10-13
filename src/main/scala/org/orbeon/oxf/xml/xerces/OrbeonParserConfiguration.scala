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
package org.orbeon.oxf.xml.xerces

import java.{util => ju}

import org.orbeon.apache.xerces.parsers.{XIncludeAwareParserConfiguration, XML11Configuration}
import org.orbeon.apache.xerces.util.ParserConfigurationSettings
import org.orbeon.oxf.xml.ParserConfiguration


object OrbeonParserConfiguration {
  private val FEATURE_NS_PREFIXES = "http://xml.org/sax/features/namespace-prefixes"
}

class OrbeonParserConfiguration(val parserConfiguration: ParserConfiguration)
  extends XIncludeAwareParserConfiguration(null, null, null) {

  private var externalEntities = false

  this.externalEntities = parserConfiguration.externalEntities

  // Set validation feature
  super.setFeature(XML11Configuration.VALIDATION, parserConfiguration.validating)

  // Set XInclude feature
  if (parserConfiguration.handleXInclude) {
    super.setFeature(XIncludeAwareParserConfiguration.XINCLUDE_FEATURE, true)
    fXIncludeHandler = new org.orbeon.apache.xerces.xinclude.XIncludeHandler
    setProperty(XIncludeAwareParserConfiguration.XINCLUDE_HANDLER, fXIncludeHandler)
    addCommonComponent(fXIncludeHandler)
  }
  else super.setFeature(XIncludeAwareParserConfiguration.XINCLUDE_FEATURE, false)


  // Used by our XercesSAXParserFactoryImpl
  def getRecognizedFeatures: ju.TreeSet[String] = {
    val result = new ju.TreeSet[String]
    result.addAll(fRecognizedFeatures.asInstanceOf[ju.List[String]])
    // Xerces uses PARSER_SETTINGS internally and makes sure that nothing from outside Xerces passes it in. But
    // we are exposing features collection here so need to remove PARSER_SETTING in case the feature set is passed
    // back in to Xerces.
    result.remove(ParserConfigurationSettings.PARSER_SETTINGS)
    // 2/16/2004 d : Xerces special cases  http://xml.org/sax/features/namespaces and
    //               http://xml.org/sax/features/namespace-prefixes and consequently they
    //               won't be available unless we add them manually.
    result.add(OrbeonParserConfiguration.FEATURE_NS_PREFIXES)
    result.add("http://xml.org/sax/features/namespaces")
    result
  }

  // Used by our XercesSAXParserFactoryImpl
  def getFeatures: ju.TreeMap[String, Boolean] = {
    val result = new ju.TreeMap[String, Boolean](fFeatures.asInstanceOf[ju.Map[String, Boolean]])
    result.remove(ParserConfigurationSettings.PARSER_SETTINGS)
    result.put("http://xml.org/sax/features/namespaces", true)
    result.put("http://xml.org/sax/features/namespace-prefixes", false)
    // 2011-08-09: Disable loading and using of DTDs when validation is turned off. If not, the effect is that, even
    // when validation is turned off:
    //
    // - internal DTDs are processed
    // - external DTDs are loaded and processed
    // - DTDs are used to set default attributes and attribute types
    // Whether this is generally expected is debatable. However, given that DTDs are rarely used in Orbeon and that
    // loading external DTDs is costly, we decide to disable this by default.
    // These two features are turned on automatically by Xerces when validation is on.
    // See: http://xerces.apache.org/xerces-j/features.html
    result.put("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
    result.put("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    result.put("http://xml.org/sax/features/external-general-entities", externalEntities)
    result.put("http://xml.org/sax/features/external-parameter-entities", externalEntities)
    result
  }
}