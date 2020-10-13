package org.orbeon.oxf.xml.xerces

import java.{lang => jl, util => ju}

import javax.xml.parsers.{SAXParser, SAXParserFactory}
import org.orbeon.oxf.xml.ParserConfiguration
import org.xml.sax.SAXNotRecognizedException

/**
 * Boasts a couple of improvements over the 'stock' xerces parser factory.
 *
 * - Doesn't create a new parser every time one calls setFeature or getFeature.  Stock one
 *   has to do this because valid feature set is encapsulated in the parser code.
 *
 * - Creates a XercesJAXPSAXParser instead of SaxParserImpl. See XercesJAXPSAXParser for
 *   why this is an improvement.
 *
 * - The improvements cut the time it takes to a SAX parser via JAXP in
 *   half and reduce the amount of garbage created when accessing '/' in
 *   the examples app from 9019216 bytes to 8402880 bytes.
 */
class XercesSAXParserFactoryImpl(parserConfiguration: ParserConfiguration)
  extends SAXParserFactory {

  // NOTE: Creating a configuration can be expensive, so callers should create factories sparingly
  private val configuration = XercesSAXParser.makeConfig(parserConfiguration)

  private val features = configuration.getFeatures
  private val recognizedFeatures = ju.Collections.unmodifiableSet(configuration.getRecognizedFeatures)

  setNamespaceAware(true)
  setValidating(parserConfiguration.validating)

  def this() =
    this(ParserConfiguration.Plain)

  @throws[SAXNotRecognizedException]
  def getFeature(key: String): Boolean = {
    if (! recognizedFeatures.contains(key))
      throw new SAXNotRecognizedException(key)
    features.get(key) eq jl.Boolean.TRUE
  }

  @throws[SAXNotRecognizedException]
  def setFeature(key: String, `val`: Boolean): Unit = {
    if (! recognizedFeatures.contains(key))
      throw new SAXNotRecognizedException(key)
    features.put(key, if (`val`)
      jl.Boolean.TRUE
    else
      jl.Boolean.FALSE
    )
  }

  def newSAXParser: SAXParser = {
    val result = new XercesJAXPSAXParser(this, features, parserConfiguration)
    // Set security manager before returning the parser
    result.setProperty("http://apache.org/xml/properties/security-manager", new org.orbeon.apache.xerces.util.SecurityManager)
    result
  }
}