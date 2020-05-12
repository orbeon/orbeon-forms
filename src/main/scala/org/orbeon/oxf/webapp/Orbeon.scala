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

import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.common.{OXFException, Version}
import org.orbeon.oxf.externalcontext.WebAppContext
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.resources.{ResourceManagerWrapper, WebAppResourceManagerImpl}
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.StringUtils._

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

// Orbeon web app initialization
object Orbeon {

  private val PropertiesProperty = "oxf.properties"
  private val LoggingProperty    = "oxf.initialize-logging"

  val OrbeonFormsAscii =
    """
      |   ____       __                        ______
      |  / __ \_____/ /_  ___  ____  ____     / ____/___  _________ ___  _____
      | / / / / ___/ __ \/ _ \/ __ \/ __ \   / /_  / __ \/ ___/ __ `__ \/ ___/
      |/ /_/ / /  / /_/ /  __/ /_/ / / / /  / __/ / /_/ / /  / / / / / (__  )
      |\____/_/  /_.___/\___/\____/_/ /_/  /_/    \____/_/  /_/ /_/ /_/____/
      |""".stripMargin

  // Initialize Orbeon
  //
  // - resource manager (based on init parameters)
  // - properties subsystem (based on run mode)
  // - version check
  // - logger (based on properties)
  // - processor registry
  def initialize(context: WebAppContext): Unit = {

    // Check whether logging initialization is disabled
    val initializeLogging = ! context.initParameters.get(LoggingProperty).contains("false")
    if (initializeLogging)
      LoggerFactory.initBasicLogger()

    // 0. Say hello
    val logger = LoggerFactory.createLogger("org.orbeon.init")

    logger.info(OrbeonFormsAscii)
    logger.info(s"Starting ${Version.VersionString}")

    // 1. Initialize the Resource Manager
    val properties = context.initParameters filter
      { case (name, value) => name.startsWith("oxf.resources.")} updated
        (WebAppResourceManagerImpl.WEB_APP_CONTEXT_KEY, context)

    logger.info(s"Initializing Resource Manager with: ${ResourceManagerWrapper.propertiesAsJson(properties)}")
    ResourceManagerWrapper.init(properties.asJava)

    // 2. Initialize properties
    val propertiesURL = {

      // Try to replace the run mode variable so we can write "oxf:/config/properties-${oxf.run-mode}.xml"
      val rawPropertiesURL =
        context.initParameters.getOrElse(
          PropertiesProperty,
          throw new OXFException("Properties file URL must be specified via `oxf.properties` in `web.xml`.")
        ).trimAllToNull

      val runMode = RunMode.getRunMode(context.initParameters)
      logger.info(s"Using run mode: $runMode")

      rawPropertiesURL.replaceAllLiterally("${" + RunMode.RunModeProperty + "}", runMode)
    }

    logger.info(s"Using root properties file: $propertiesURL")
    Properties.init(propertiesURL)

    // 3. Initialize Version object (depends on resource manager)
    // Better to do it here so that log messages will go to the same place as the above logs
    Version.instance

    // 4. Initialize log4j with a DOMConfiguration
    if (initializeLogging)
      LoggerFactory.initLogger()

    // 5. Log properties in debug mode *after* updated logger configuration
    try {
      val json = Properties.instance.getPropertySet.allPropertiesAsJson
      Properties.logger.debug(s"All properties read: $json")
    } catch {
      case NonFatal(t) =>
        logger.error(OrbeonFormatter.format(t))
    }

    // 6. Register processor definitions with the default XML Processor Registry
    InitUtils.processorDefinitions
  }
}
