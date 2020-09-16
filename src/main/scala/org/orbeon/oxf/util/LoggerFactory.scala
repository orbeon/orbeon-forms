/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.util

import org.apache.log4j
import org.apache.log4j.xml.DOMConfigurator
import org.log4s
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.InitUtils._
import org.orbeon.oxf.processor.{DOMSerializer, ProcessorImpl}
import org.orbeon.oxf.properties.Properties

import scala.util.control.NonFatal

object LoggerFactory {

  private val Log4jDomConfigProperty = "oxf.log4j-config"

  val logger: org.log4s.Logger = createLogger(getClass)

  def createLogger(name: String)   : log4s.Logger = org.log4s.getLogger(name)
  def createLogger(clazz: Class[_]): log4s.Logger = org.log4s.getLogger(clazz.getName)

  def createLoggerJava(name: String):    org.slf4j.Logger = createLogger(name).logger
  def createLoggerJava(clazz: Class[_]): org.slf4j.Logger = createLogger(clazz).logger

  /**
   * Init basic config until resource manager is setup.
   */
  def initBasicLogger(): Unit = {
    // See http://discuss.orbeon.com/Problem-with-log-in-orbeon-with-multiple-webapp-td36786.html
    // LogManager.resetConfiguration()
    val root = log4j.Logger.getRootLogger
    root.setLevel(log4j.Level.INFO)
    root.addAppender(
      new log4j.ConsoleAppender(
        new log4j.PatternLayout(log4j.PatternLayout.DEFAULT_CONVERSION_PATTERN),
        log4j.ConsoleAppender.SYSTEM_ERR
      )
    )
  }

  /**
   * Init log4j. Needs Orbeon Forms Properties system up and running.
   */
  def initLogger(): Unit =
    try {
      // Accept both `xs:string` and `xs:anyURI` types
      val propertySet = Properties.instance.getPropertySet
      if (propertySet eq null)
        throw new OXFException("Property set not found.")
      propertySet.getStringOrURIAsStringOpt(Log4jDomConfigProperty, allowEmpty = false) match {
        case Some(log4jConfigURL) =>
          val urlGenerator = PipelineUtils.createURLGenerator(log4jConfigURL, true)
          val domSerializer = new DOMSerializer
          PipelineUtils.connect(urlGenerator, ProcessorImpl.OUTPUT_DATA, domSerializer, ProcessorImpl.INPUT_DATA)

          val element =
            withPipelineContext { pipelineContext =>
              urlGenerator.reset(pipelineContext)
              domSerializer.reset(pipelineContext)
              domSerializer.runGetW3CDocument(pipelineContext).getDocumentElement
            }

          DOMConfigurator.configure(element)
        case None =>
          logger.info(s"Property `$Log4jDomConfigProperty` not set. Skipping logging initialization.")
      }
    } catch {
      case NonFatal(t) =>
        logger.error(t)("Cannot load Log4J configuration. Skipping logging initialization")
    }
}