package org.orbeon.oxf.xml.xerces

import org.orbeon.oxf.xml.ParserConfiguration

/*
* An improvement over org.orbeon.apache.xerces.parsers.SAXParser.  Every time
* org.orbeon.apache.xerces.parsers.SAXParser is constructed it looks in
* META-INF/services/orbeon.apache.xerces.xni.parser.XMLParserConfiguration to figure out what
* config to use.  Pbms with this are
*
* - We only want our config to be used. While we have changed the above file, any work done
*   to read the file is really just a waste.
*
* - The contents of the file do not change at runtime so there is little point in repeatedly
*   reading it.
*
* - About 16.4K of garbage gets create with each read of the above file. At the frequency with
*   which OPS creates SAX parsers this accumulates quickly and consequently we start losing processor time to the
*   garbage collector.
*/
object XercesSAXParser {

  private val NOTIFY_BUILTIN_REFS = "http://apache.org/xml/features/scanner/notify-builtin-refs"
  private val SYMBOL_TABLE = "http://apache.org/xml/properties/internal/symbol-table"
  private val XMLGRAMMAR_POOL = "http://apache.org/xml/properties/internal/grammar-pool"

  private[xerces] val RECOGNIZED_FEATURES = Array(NOTIFY_BUILTIN_REFS)
  private[xerces] val RECOGNIZED_PROPERTIES = Array(SYMBOL_TABLE, XMLGRAMMAR_POOL)

  def makeConfig(parserConfiguration: ParserConfiguration): OrbeonParserConfiguration = {
    val result = new OrbeonParserConfiguration(parserConfiguration)
    result.addRecognizedFeatures(RECOGNIZED_FEATURES)
    result.setFeature(NOTIFY_BUILTIN_REFS, true)
    result.addRecognizedProperties(RECOGNIZED_PROPERTIES)
    result
  }
}

class XercesSAXParser(val parserConfiguration: ParserConfiguration)
  extends org.orbeon.apache.xerces.parsers.SAXParser(XercesSAXParser.makeConfig(parserConfiguration))