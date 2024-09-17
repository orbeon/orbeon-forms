/*
 *
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 2000-2005 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Xerces" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation and was
 * originally based on software copyright (c) 1999, Sun Microsystems, Inc.,
 * http://www.sun.com.  For more information on the Apache Software
 * Foundation, please see <http://www.apache.org/>.
 *//*
 *
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 2000-2005 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Xerces" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation and was
 * originally based on software copyright (c) 1999, Sun Microsystems, Inc.,
 * http://www.sun.com.  For more information on the Apache Software
 * Foundation, please see <http://www.apache.org/>.
 */
package org.orbeon.oxf.xml.xerces

import org.orbeon.apache.xerces.impl.Constants
import org.orbeon.apache.xerces.jaxp.JAXPConstants
import org.orbeon.apache.xerces.util.SAXMessageFormatter
import org.orbeon.oxf.xml.ParserConfiguration
import org.xml.sax.*
import org.xml.sax.helpers.DefaultHandler
import javax.xml.parsers.SAXParserFactory
import java.{util => ju}

import scala.jdk.CollectionConverters.*

/**
 * The only real difference between this class and org.orbeon.apache.xerces.jaxp.SAXParserImpl is that this class
 * constructs an instance of org.orbeon.oxf.xml.XercesSAXParser instead of org.apache.xerces.parsers.SAXParser. For why
 * this is an improvement see XercesSAXParser.
 *
 * 02/16/2005 d : Got rid of 'implements JAXPConstants'. Aside from this being a stupid pattern, it was causing a
 * problem in Tomcat. Pbm was that in TC the following was happening :
 *
 * o TC creates web app contexts, each loaded with a web app loader.
 *
 * o Each context tries to get defaults from server/conf/web.xml using commons-digester.
 *
 * o commons-digester uses JAXP to load xml reader.
 *
 * o JAXP uses web app loader to load sax parser factory and finds our loader.
 *
 * o During the execution of XercesJAXPSAXParserFactoryImpl.class.newInstance()
 * the class XercesJAXPSAXParserFactoryImpl is fully resolved.
 *
 * o The above leads to load of this class, XercesJAXPSAXParser, which leads to load of JAXPConstants.
 *
 * Now since XercesJAXPSAXParserFactoryImpl.&lt;clinit&gt; hasn't run at the prior to the load of JAXPConstants we get
 * NoClassDefFoundException. (&lt;clinit&gt; adds jars in Class-Path of orbeon.jar manifest to TC's class loader since
 * it incorrectly ignores the Class-Path.)
 */
object XercesJAXPSAXParser {

  private[xerces] object XercesDefaultValidationErrorHandler {
    private val ERROR_COUNT_LIMIT = 10
  }

  private[xerces] class XercesDefaultValidationErrorHandler extends DefaultHandler {
    private var errorCount = 0

    // TODO: Fix message i18n.
    @throws[SAXException]
    override def error(e: SAXParseException): Unit = {
      if (errorCount >= XercesDefaultValidationErrorHandler.ERROR_COUNT_LIMIT) {
        // Ignore all errors after reaching the limit
        return
      } else if (errorCount == 0) {
        // Print a warning before the first error
        System.err.println("Warning: validation was turned on but an org.xml.sax.ErrorHandler was not")
        System.err.println("set, which is probably not what is desired.  Parser will use a default")
        System.err.println("ErrorHandler to print the first " + XercesDefaultValidationErrorHandler.ERROR_COUNT_LIMIT + " errors.  Please call")
        System.err.println("the 'setErrorHandler' method to fix this.")
      }
      var systemId = e.getSystemId
      if (systemId == null)
        systemId = "null"
      val message = "Error: URI=" + systemId + " Line=" + e.getLineNumber + ": " + e.getMessage
      System.err.println(message)
      errorCount += 1
    }
  }
}

class XercesJAXPSAXParser private[xerces] (val spf: SAXParserFactory, val features: ju.Map[String, Boolean], val parserConfiguration: ParserConfiguration)
extends javax.xml.parsers.SAXParser {

  // Instantiate a SAXParser directly and not through SAX so that we
  // use the right ClassLoader
  private val xmlReader = new XercesSAXParser(parserConfiguration)

  // If validating, provide a default ErrorHandler that prints
  // validation errors with a warning telling the user to set an
  // ErrorHandler.
  if (spf.isValidating)
    xmlReader.setErrorHandler (new XercesJAXPSAXParser.XercesDefaultValidationErrorHandler)

  xmlReader.setFeature (Constants.SAX_FEATURE_PREFIX + Constants.VALIDATION_FEATURE, spf.isValidating)

  // JAXP "namespaceAware" == SAX Namespaces feature
  // Note: there is a compatibility problem here with default values:
  // JAXP default is false while SAX 2 default is true!
  xmlReader.setFeature (Constants.SAX_FEATURE_PREFIX + Constants.NAMESPACES_FEATURE, spf.isNamespaceAware)

  // SAX "namespaces" and "namespace-prefixes" features should not
  // both be false.  We make them opposite for backward compatibility
  // since JAXP 1.0 apps may want to receive xmlns* attributes.
  xmlReader.setFeature (Constants.SAX_FEATURE_PREFIX + Constants.NAMESPACE_PREFIXES_FEATURE, ! spf.isNamespaceAware)

  // Set any features of our XMLReader based on any features set on the SAXParserFactory.
  for (entry <- features.entrySet.asScala)
    xmlReader.setFeature (entry.getKey, entry.getValue)

  private var schemaLanguage: String = null // null means DTD

  def getParser: Parser = {
    // Xerces2 AbstractSAXParser implements SAX1 Parser
    // assert(xmlReader instanceof Parser);
    xmlReader.asInstanceOf[Parser]
  }

  /**
   * Returns the XMLReader that is encapsulated by the implementation of this class.
   */
  def getXMLReader: XMLReader = xmlReader

  def isNamespaceAware: Boolean =
    try
      xmlReader.getFeature(Constants.SAX_FEATURE_PREFIX + Constants.NAMESPACES_FEATURE)
    catch {
      case x: SAXException =>
        throw new IllegalStateException (x.getMessage)
    }

  def isValidating: Boolean =
    try
      xmlReader.getFeature (Constants.SAX_FEATURE_PREFIX + Constants.VALIDATION_FEATURE)
    catch {
      case x: SAXException =>
        throw new IllegalStateException (x.getMessage)
    }

  /**
   * Sets the particular property in the underlying implementation of org.xml.sax.XMLReader.
   */
  @throws[SAXNotRecognizedException]
  @throws[SAXNotSupportedException]
  def setProperty (name: String, value: Any): Unit =
    if (JAXPConstants.JAXP_SCHEMA_LANGUAGE == name) {
      // JAXP 1.2 support
      if (JAXPConstants.W3C_XML_SCHEMA == value) {
        //None of the properties will take effect till the setValidating(true) has been called
        if (isValidating) {
          schemaLanguage = JAXPConstants.W3C_XML_SCHEMA
          xmlReader.setFeature(Constants.XERCES_FEATURE_PREFIX + Constants.SCHEMA_VALIDATION_FEATURE, true)
        // this will allow the parser not to emit DTD-related
        // errors, as the spec demands
        xmlReader.setProperty(JAXPConstants.JAXP_SCHEMA_LANGUAGE, JAXPConstants.W3C_XML_SCHEMA)
        }
      } else {
        if (value == null) {
          schemaLanguage = null
          xmlReader.setFeature (Constants.XERCES_FEATURE_PREFIX + Constants.SCHEMA_VALIDATION_FEATURE, false)
        } else {
          // REVISIT: It would be nice if we could format this message
          // using a user specified locale as we do in the underlying
          // XMLReader -- mrglavas
          throw new SAXNotSupportedException (SAXMessageFormatter.formatMessage (null, "schema-not-supported", null) )
        }
      }
    } else {
      if (JAXPConstants.JAXP_SCHEMA_SOURCE == name) {
        val `val` = getProperty(JAXPConstants.JAXP_SCHEMA_LANGUAGE).asInstanceOf[String]
        if (`val` != null && JAXPConstants.W3C_XML_SCHEMA == `val`)
          xmlReader.setProperty (name, value)
        else
          throw new SAXNotSupportedException (SAXMessageFormatter.formatMessage (null, "jaxp-order-not-supported", Array[AnyRef] (JAXPConstants.JAXP_SCHEMA_LANGUAGE, JAXPConstants.JAXP_SCHEMA_SOURCE) ) )
      } else {
        xmlReader.setProperty (name, value)
      }
    }

  /**
   * Returns the particular property requested for in the underlying implementation of org.xml.sax.XMLReader.
   */
  @throws[SAXNotRecognizedException]
  @throws[SAXNotSupportedException]
  def getProperty (name: String): AnyRef =
    if (JAXPConstants.JAXP_SCHEMA_LANGUAGE == name)
      schemaLanguage
    else
      xmlReader.getProperty (name)
}