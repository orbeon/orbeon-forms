package org.orbeon.oxf.xml.xerces

import cats.Eval
import org.slf4j
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CoreUtils.PipeOps
import org.orbeon.oxf.xml.ParserConfiguration
import org.xml.sax.SAXNotRecognizedException

import org.orbeon.oxf.util.SLF4JLogging._

import java.{lang => jl, util => ju}
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

    import XercesSAXParserFactoryImpl._

    val resultUsingPropertiesOpt =
      Properties.withAcquiredPropertiesOrSkip {
        // The first XML parsing takes place when we load and parse configuration properties. If we are in the process
        // of loading configuration properties, this block will not run and we will use a default `SecurityManager`
        // below. If this block runs, it means that we have acquired the semaphore and that the configuration properties
        // are available, and we can safely use configuration properties, and therefore initialize the global security
        // manager.
        // Similarly, this will not run while updating properties.
        trace(s"newSAXParser: obtained configuration properties semaphore, using global XML parser security manager")
        new XercesJAXPSAXParser(this, features, parserConfiguration) |!>
          (_.setProperty(SecurityManagerProperty, securityManager))
      }

    resultUsingPropertiesOpt.getOrElse {
      trace(s"newSAXParser: did not obtain configuration properties semaphore, using default XML parser security manager")
      new XercesJAXPSAXParser(this, features, parserConfiguration) |!>
        (_.setProperty(SecurityManagerProperty, DefaultSecurityManager))
    }
  }
}

private object XercesSAXParserFactoryImpl {

  implicit def logger: slf4j.Logger = Properties.logger.logger

  val SecurityManagerProperty = "http://apache.org/xml/properties/security-manager"

  // "0" means "one expansion allowed", etc. Off-by-one error in Xerces! So we subtract 1 from the value passed.
  private def newSecurityManager(entityExpansionLimit: Int): org.orbeon.apache.xerces.util.SecurityManager = {
    new org.orbeon.apache.xerces.util.SecurityManager |!> {
        _.setEntityExpansionLimit((entityExpansionLimit |!>
          (limit => info(s"newSecurityManager: setting entity expansion limit to: $limit")) ) - 1)
    }
  }

  private def getPropertiesEntityExpansionLimit: Int =
    Properties.instance.getPropertySet.getInteger("oxf.xml-parsing.entity-expansion-limit", default = 0)

  private val DefaultSecurityManager = newSecurityManager(0)

  @volatile private var _securityManager: Eval[org.orbeon.apache.xerces.util.SecurityManager] = Eval.later {
    newSecurityManager(getPropertiesEntityExpansionLimit)
  }

  private def securityManager: org.orbeon.apache.xerces.util.SecurityManager = {

    val currentSecurityManager       = _securityManager.value
    val upToDateEntityExpansionLimit = getPropertiesEntityExpansionLimit

    if (upToDateEntityExpansionLimit != currentSecurityManager.getEntityExpansionLimit + 1) {
      _securityManager = Eval.now {
        newSecurityManager(upToDateEntityExpansionLimit)
      }
      _securityManager.value
    } else {
      currentSecurityManager
    }
  }
}