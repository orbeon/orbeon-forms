package org.orbeon.oxf.xml.xerces

import cats.Eval
import org.orbeon.oxf.properties.PropertyLoader
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.SLF4JLogging.*
import org.orbeon.oxf.xml.ParserConfiguration
import org.slf4j
import org.xml.sax.SAXNotRecognizedException

import java.util as ju
import javax.xml.parsers.{SAXParser, SAXParserFactory}


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
    features.get(key)
  }

  @throws[SAXNotRecognizedException]
  def setFeature(name: String, value: Boolean): Unit = {
    if (! recognizedFeatures.contains(name))
      throw new SAXNotRecognizedException(name)
    features.put(name, value)
  }

  import XercesSAXParserFactoryImpl.*

  def newSAXParser: SAXParser = {
    trace(s"newSAXParser: obtained configuration properties semaphore, using global XML parser security manager")
    new XercesJAXPSAXParser(this, features, parserConfiguration) |!>
      (_.setProperty(XercesSecurityManagerProperty, securityManager))
  }
}

private object XercesSAXParserFactoryImpl {

  implicit def logger: slf4j.Logger = PropertyLoader.logger.logger

  private val OrbeonEntityExpansionLimitProperty = "oxf.xml-parsing.entity-expansion-limit"
  private val XercesSecurityManagerProperty      = "http://apache.org/xml/properties/security-manager"

  // "0" means "one expansion allowed", etc. Off-by-one error in Xerces! So we subtract 1 from the value passed.
  private def newSecurityManager(entityExpansionLimit: Int): org.orbeon.apache.xerces.util.SecurityManager = {
    new org.orbeon.apache.xerces.util.SecurityManager |!> {
      _.setEntityExpansionLimit((entityExpansionLimit |!>
        (limit => info(s"newSecurityManager: setting entity expansion limit to: $limit")) ) - 1)
    }
  }

  private def getPropertiesEntityExpansionLimit: Int =
    PropertyLoader.getPropertyStore(None).globalPropertySet.getInteger(OrbeonEntityExpansionLimitProperty, default = 0)

  @volatile private var _securityManager: Eval[org.orbeon.apache.xerces.util.SecurityManager] = Eval.later {
    newSecurityManager(getPropertiesEntityExpansionLimit)
  }

  private def securityManager: org.orbeon.apache.xerces.util.SecurityManager = {

    val currentSecurityManager       = _securityManager.value
    val upToDateEntityExpansionLimit = getPropertiesEntityExpansionLimit

    if (upToDateEntityExpansionLimit != currentSecurityManager.getEntityExpansionLimit + 1) { // Xerces off-by-one error
      _securityManager = Eval.now {
        newSecurityManager(upToDateEntityExpansionLimit)
      }
      _securityManager.value
    } else {
      currentSecurityManager
    }
  }
}