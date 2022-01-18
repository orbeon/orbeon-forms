package org.orbeon.oxf.util

import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.config.{AbstractConfiguration, ConfigurationSource, Configurator}
import org.apache.logging.log4j.core.layout.PatternLayout
import org.apache.logging.log4j.{Level, LogManager}
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.CoreUtils.PipeOps
import org.orbeon.oxf.util.LoggerFactory.logger
import org.orbeon.oxf.xml.{ParserConfiguration, XMLParsing}

import java.io.InputStream
import java.net.URL
import scala.util.Try
import scala.util.control.NonFatal


object Log4jSupport {

  import Private._

  // Initialize a basic logging configuration until the resource manager is setup
  def initBasicLogger(): Unit = {
    // See http://discuss.orbeon.com/Problem-with-log-in-orbeon-with-multiple-webapp-td36786.html
    // LogManager.resetConfiguration()
    Configurator.reconfigure(new OrbeonDefaultConfiguration)
  }

  // Initialize Log4j, trying configurations. This requires that the resource manager and properties are available.
  def initLogger(): Unit =
    try {
      val propertySet = Properties.instance.getPropertySetOrThrow

      val propsWithFns =
        ConfigPropNamesWithFns map { case (propName, fn) =>
          (propName, propertySet.getStringOrURIAsStringOpt(propName, allowEmpty = false), fn)
        } collect { case (propName, Some(propValue), fn) =>
          (propName, propValue, fn)
        }

      val log4jContext = LogManager.getContext(false).asInstanceOf[LoggerContext]

      val resultStream =
        propsWithFns.toStream map { case (propName, propValue, fn) =>

          def tryToEither[T](t: Try[T], wrap: Throwable => LoggerInitError) =
            t.toEither.left.map(wrap)

          def tryUrl: Either[LoggerInitError, URL] =
            tryToEither(
              Try(URLFactory.createURL(propValue)),
              LoggerInitError.MalformedUrl
            )

          def tryToFindFile(url: URL): Either[LoggerInitError, InputStream] =
            tryToEither(
              Try(url.openStream()),
              LoggerInitError.NotFound
            )

          // When a parsing error or other error occurs with the XML configuration, Log4j2 logs it but then
          // swallows it! So we do our own parsing first so we can detect and report the problem.
          def tryToParseFile(is: InputStream): Either[LoggerInitError, Unit] =
            tryToEither(
              XMLParsing.tryParsingXml(is, propValue, ParserConfiguration.XIncludeOnly),
              LoggerInitError.InvalidXml
            )

          // `reconfigure()` logs and swallows all `Exception`s!
          def tryToConfigure(is: InputStream, url: URL): Either[LoggerInitError, Unit] =
            tryToEither(
              Try(useAndClose(is)(_ => Configurator.reconfigure(fn(log4jContext, is, url)))),
              LoggerInitError.Other
            )

          val tryResult =
            for {
              url <- tryUrl
              is1 <- tryToFindFile(url)
              _   <- tryToParseFile(is1)
              is2 <- tryToFindFile(url)
              _   <- tryToConfigure(is2, url)
            } yield
              ()

          (tryResult, propName, propValue)
        }

      def makePair(propName: String, propValue: String) =
        s"property `$propName` and configuration at URL `$propValue`"

      resultStream collectFirst { case (Right(_), propName, propValue) => (propName, propValue) } match {
        case Some((propName, propValue)) if propName == Log4j1ConfigPropName =>
          logger.info(s"Configured Log4j 2 in Log4j 1 backward compatibility mode using ${makePair(propName, propValue)}.")
        case Some((propName, propValue)) =>
          logger.info(s"Configured Log4j 2 using ${makePair(propName, propValue)}.")
        case None =>

          // `collectFirst` and `match` above ensure the `Stream` is complete
          val errorDetails =
            resultStream collect {
              case (Left(LoggerInitError.MalformedUrl(t)), propName, propValue) =>
                t -> s"- property `$propName`: URL `$propValue` is malformed"
              case (Left(LoggerInitError.NotFound(t)),     propName, propValue) =>
                t -> s"- property `$propName`: URL `$propValue` not found"
              case (Left(LoggerInitError.InvalidXml(t)),   propName, propValue) =>
                t -> s"- property `$propName`: XML parsing error while reading URL `$propValue`"
              case (Left(LoggerInitError.Other(t)),        propName, propValue) =>
                t -> s"- property `$propName`: other error while reading URL `$propValue`"
            }

          def addThrowableMessage(t: Throwable, message: String) =
            s"$message ${OrbeonFormatter.getThrowableMessage(t).map(m => s"($m)").getOrElse("")}"

        logger.error(
          "No valid Log4j configuration found. Skipping custom logging initialization." #::
            errorDetails.map((addThrowableMessage _).tupled) mkString "\n"
        )
      }

    } catch {
      case NonFatal(t) =>
        logger.error(t)("Cannot load Log4j configuration. Skipping custom logging initialization.")
    }

  // Inspired by Log4j2 `DefaultConfiguration`
  class OrbeonDefaultConfiguration
    extends AbstractConfiguration(null, ConfigurationSource.NULL_SOURCE) {

    setToDefault()

    override protected def setToDefault(): Unit = {
      setName(DefaultConfigurationName)
      val layout  = PatternLayout.newBuilder.withPattern(DefaultPattern).withConfiguration(this).build
      val appender = ConsoleAppender.createDefaultAppenderForLayout(layout) |!> (_.start())
      appender.start()
      addAppender(appender)
      getRootLogger |!>
        (_.addAppender(appender, null, null)) |!>
        (_.setLevel(DefaultLevel))
    }

    override protected def doConfigure(): Unit = ()
  }

  private object Private {

    sealed trait LoggerInitError
    object LoggerInitError {
      case class MalformedUrl(t: Throwable) extends LoggerInitError
      case class NotFound    (t: Throwable) extends LoggerInitError
      case class InvalidXml  (t: Throwable) extends LoggerInitError
      case class Other       (t: Throwable) extends LoggerInitError
    }

    val DefaultConfigurationName = "OrbeonDefault"
    val DefaultLevel             = Level.INFO
    val DefaultPattern           = "%date{ISO8601} %-5level %logger{1} - %message%n"

    val Log4j1ConfigPropName     = "oxf.log4j-config"
    val Log4j2ConfigPropName     = "oxf.log4j2-config"

    val ConfigPropNamesWithFns: List[(String, (LoggerContext, InputStream, URL) => AbstractConfiguration)] = List(
      Log4j1ConfigPropName -> createLog4j1XmlConfig,
      Log4j2ConfigPropName -> createLog4j2XmlConfig
    )

    def createLog4j1XmlConfig(cxt: LoggerContext, is: InputStream, url: URL): AbstractConfiguration =
      new org.apache.log4j.xml.XmlConfiguration(cxt, new ConfigurationSource(is, url), 0)

    def createLog4j2XmlConfig(cxt: LoggerContext, is: InputStream, url: URL): AbstractConfiguration =
      new org.apache.logging.log4j.core.config.xml.XmlConfiguration(cxt, new ConfigurationSource(is, url))
  }
}