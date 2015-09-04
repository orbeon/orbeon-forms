/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.webapp

import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.resources.{ResourceManagerWrapper, WebAppResourceManagerImpl}
import org.apache.commons.lang3.StringUtils
import org.orbeon.oxf.common.{OXFException, Version}
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.pipeline.InitUtils
import collection.JavaConverters._

// Orbeon web app initialization
object Orbeon {

  private val PropertiesProperty = "oxf.properties"
  private val LoggingProperty    = "oxf.initialize-logging"
  private val logger = LoggerFactory.createLogger(Orbeon.getClass)

  // Initialize Orbeon
  //
  // - resource manager (based on init parameters)
  // - properties subsystem (based on run mode)
  // - version check
  // - logger (based on properties)
  // - processor registry
  def initialize(context: WebAppContext) = {
    // Check whether logging initialization is disabled
    val initializeLogging = ! context.initParameters.get(LoggingProperty).contains("false")
    if (initializeLogging)
      LoggerFactory.initBasicLogger()

    // 0. Say hello
    logger.info("Starting " + Version.VersionString)

    // 1. Initialize the Resource Manager
    val properties = context.initParameters filter
      { case (name, value) ⇒ name.startsWith("oxf.resources.")} updated
        (WebAppResourceManagerImpl.WEB_APP_CONTEXT_KEY, context) asJava

    logger.info("Initializing Resource Manager with: " + properties)
    ResourceManagerWrapper.init(properties)

    // 2. Initialize properties
    val propertiesURL = {

      // Try to replace the run mode variable so we can write "oxf:/config/properties-${oxf.run-mode}.xml"
      val rawPropertiesURL =
        StringUtils.trimToNull(
          context.initParameters.getOrElse(
            PropertiesProperty,
            throw new OXFException("Properties file URL must be specified via oxf.properties in web.xml.")
          )
        )

      val runMode = RunMode.getRunMode(context.initParameters)
      logger.info("Using run mode: " + runMode)

      rawPropertiesURL.replaceAllLiterally("${" + RunMode.RunModeProperty + "}", runMode)
    }
    logger.info("Using properties file: " + propertiesURL)
    Properties.init(propertiesURL)

    // 3. Initialize Version object (depends on resource manager)
    // Better to do it here so that log messages will go to the same place as the above logs
    Version.instance

    // 4. Initialize log4j with a DOMConfiguration
    if (initializeLogging)
      LoggerFactory.initLogger()

    // 5. Register processor definitions with the default XML Processor Registry
    InitUtils.processorDefinitions
  }
}
