package org.orbeon.oxf.util

import org.apache.log4j
import org.apache.log4j.xml.DOMConfigurator
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.InitUtils.withPipelineContext
import org.orbeon.oxf.processor.{DOMSerializer, ProcessorImpl}
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.LoggerFactory.logger

import scala.util.control.NonFatal


object Log4jSupport {

  private val Log4jDomConfigProperty = "oxf.log4j-config"

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
