/**
 * Copyright (C) 2023 Orbeon, Inc.
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
package org.orbeon.oxf.servlet

import org.log4s.Logger
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.StringUtils._

import java.{util => ju}
import scala.jdk.CollectionConverters.enumerationAsScalaIteratorConverter

class JavaxServletContainerInitializer extends javax.servlet.ServletContainerInitializer with CommonContainerInitializer {
  protected val logger: Logger = LoggerFactory.createLogger(classOf[JavaxServletContainerInitializer])

  type ServletContextListener = javax.servlet.ServletContextListener
  type HttpSessionListener    = javax.servlet.http.HttpSessionListener

  override val orbeonServletClass: Class[_ <: JavaxOrJakartaServlet]       = classOf[JavaxOrbeonServlet]
  override val limiterFilterClass: Class[_ <: JavaxOrJakartaFilter]        = classOf[JavaxLimiterFilter]
  override val formRunnerAuthFilterClass: Class[_ <: JavaxOrJakartaFilter] = classOf[JavaxFormRunnerAuthFilter]
  override val orbeonXFormsFilterClass: Class[_ <: JavaxOrJakartaFilter]   = classOf[JavaxOrbeonXFormsFilter]

  import org.orbeon.oxf.cache.JavaxShutdownListener
  import org.orbeon.oxf.webapp.{JavaxOrbeonServletContextListener, JavaxOrbeonSessionListener}
  import org.orbeon.oxf.xforms.{JavaxReplicationServletContextListener, JavaxXFormsServletContextListener}

  override val orbeonServletContextListenerClass: Class[_ <: ServletContextListener]      = classOf[JavaxOrbeonServletContextListener]
  override val replicationServletContextListenerClass: Class[_ <: ServletContextListener] = classOf[JavaxReplicationServletContextListener]
  override val xFormsServletContextListenerClass: Class[_ <: HttpSessionListener]         = classOf[JavaxXFormsServletContextListener]
  override val orbeonSessionListenerClass: Class[_ <: HttpSessionListener]                = classOf[JavaxOrbeonSessionListener]
  override val shutdownListenerClass: Class[_ <: ServletContextListener]                  = classOf[JavaxShutdownListener]

  override def onStartup(c: ju.Set[Class[_]], ctx: javax.servlet.ServletContext): Unit = startup(c, ServletContext(ctx))
}

class JakartaServletContainerInitializer extends jakarta.servlet.ServletContainerInitializer with CommonContainerInitializer {
  protected val logger: Logger = LoggerFactory.createLogger(classOf[JakartaServletContainerInitializer])

  type ServletContextListener = jakarta.servlet.ServletContextListener
  type HttpSessionListener    = jakarta.servlet.http.HttpSessionListener

  override val orbeonServletClass: Class[_ <: JavaxOrJakartaServlet]       = classOf[JakartaOrbeonServlet]
  override val limiterFilterClass: Class[_ <: JavaxOrJakartaFilter]        = classOf[JakartaLimiterFilter]
  override val formRunnerAuthFilterClass: Class[_ <: JavaxOrJakartaFilter] = classOf[JakartaFormRunnerAuthFilter]
  override val orbeonXFormsFilterClass: Class[_ <: JavaxOrJakartaFilter]   = classOf[JakartaOrbeonXFormsFilter]

  import org.orbeon.oxf.cache.JakartaShutdownListener
  import org.orbeon.oxf.webapp.{JakartaOrbeonServletContextListener, JakartaOrbeonSessionListener}
  import org.orbeon.oxf.xforms.{JakartaReplicationServletContextListener, JakartaXFormsServletContextListener}

  override val orbeonServletContextListenerClass: Class[_ <: ServletContextListener]      = classOf[JakartaOrbeonServletContextListener]
  override val replicationServletContextListenerClass: Class[_ <: ServletContextListener] = classOf[JakartaReplicationServletContextListener]
  override val xFormsServletContextListenerClass: Class[_ <: HttpSessionListener]         = classOf[JakartaXFormsServletContextListener]
  override val orbeonSessionListenerClass: Class[_ <: HttpSessionListener]                = classOf[JakartaOrbeonSessionListener]
  override val shutdownListenerClass: Class[_ <: ServletContextListener]                  = classOf[JakartaShutdownListener]

  override def onStartup(c: ju.Set[Class[_]], ctx: jakarta.servlet.ServletContext): Unit = startup(c, ServletContext(ctx))
}

trait CommonContainerInitializer {
  protected def logger: Logger

  type ServletContextListener <: ju.EventListener // javax/jakarta.servlet.ServletContextListener
  type HttpSessionListener    <: ju.EventListener // javax/jakarta.servlet.http.HttpSessionListener

  def orbeonServletClass: Class[_ <: JavaxOrJakartaServlet]
  def limiterFilterClass: Class[_ <: JavaxOrJakartaFilter]
  def formRunnerAuthFilterClass: Class[_ <: JavaxOrJakartaFilter]
  def orbeonXFormsFilterClass: Class[_ <: JavaxOrJakartaFilter]

  def orbeonServletContextListenerClass: Class[_ <: ServletContextListener]
  def replicationServletContextListenerClass: Class[_ <: ServletContextListener]
  def xFormsServletContextListenerClass: Class[_ <: HttpSessionListener]
  def orbeonSessionListenerClass: Class[_ <: HttpSessionListener]
  def shutdownListenerClass: Class[_ <: ServletContextListener]

  private type ServletOrFilterRegistration = {
    def setInitParameter(name: String, value: String): Boolean
  }

  def startup(c: ju.Set[Class[_]], ctx: ServletContext): Unit = {
    logger.info("Registering Orbeon servlets, filters, and listeners")

    // Servlets

    val mainRegistrationResult = registerServlet(
      ctx                 = ctx,
      servletName         = "orbeon-main-servlet",
      servletClass        = orbeonServletClass,
      initParamPrefix     = "oxf.",
      mandatoryInitParams = Set(
        "oxf.main-processor.name",
        "oxf.main-processor.input.config",
        "oxf.error-processor.name",
        "oxf.error-processor.input.controller",
        "oxf.http.accept-methods"
      )
    )

    val renderedRegistrationResult = registerServlet(
      ctx                 = ctx,
      servletName         = "orbeon-renderer-servlet",
      servletClass        = orbeonServletClass,
      initParamPrefix     = "oxf.",
      mandatoryInitParams = Set(
        "oxf.main-processor.name",
        "oxf.main-processor.input.controller",
        "oxf.error-processor.name",
        "oxf.error-processor.input.config"
      )
    )

    // Filters

    // Limit concurrent access to Form Runner
    val limiterRegistrationResult = registerFilter(
      ctx                 = ctx,
      filterName          = "orbeon-limiter-filter",
      filterClass         = limiterFilterClass,
      dispatcherTypes     = Set(DispatcherType.REQUEST),
      initParamPrefix     = "",
      mandatoryInitParams = Set("include", "exclude", "min-threads", "num-threads", "max-threads")
    )

    // Add internal Orbeon-* headers for auth
    val authRegistrationResult = registerFilter(
      ctx                 = ctx,
      filterName          = "orbeon-form-runner-auth-servlet-filter",
      filterClass         = formRunnerAuthFilterClass,
      dispatcherTypes     = Set(DispatcherType.REQUEST, DispatcherType.FORWARD),
      initParamPrefix     = "",
      mandatoryInitParams = Set.empty
    )

    // All JSP files under /xforms-jsp go through the XForms filter
    val xformsRegistrationResult = registerFilter(
      ctx                 = ctx,
      filterName          = "orbeon-xforms-filter",
      filterClass         = orbeonXFormsFilterClass,
      dispatcherTypes     = Set(DispatcherType.REQUEST, DispatcherType.FORWARD),
      initParamPrefix     = "oxf.xforms.renderer.",
      mandatoryInitParams = Set.empty
    )

    val registrationResult = RegistrationResult.merged(
      Seq(
        mainRegistrationResult,
        renderedRegistrationResult,
        limiterRegistrationResult,
        authRegistrationResult,
        xformsRegistrationResult
      )
    )

    if (registrationResult.missingInitParams.nonEmpty) {
      logger.error(s"One or more init parameters are missing, please update your web.xml configuration")
    } else {
      // If the servlets and filters could be registered correctly, register listeners as well

      // Listeners

      registerListener(ctx, "orbeon-servlet-context-listener",      orbeonServletContextListenerClass)
      // Context listener for deployment with replication
      registerListener(ctx, "replication-servlet-context-listener", replicationServletContextListenerClass)
      // XForms session listener
      registerListener(ctx, "xforms-servlet-context-listener",      xFormsServletContextListenerClass)
      // General-purpose session listener
      registerListener(ctx, "orbeon-session-listener",              orbeonSessionListenerClass)
      // Cache shutdown listener
      registerListener(ctx, "shutdown-listener",                    shutdownListenerClass)
    }
  }

  private val EnabledParamPart    = "enabled"
  private val MappingParamPart    = "mapping"
  private val UrlPatternParamPart = "url-pattern"

  private def paramPrefix(servletOrFilterOrListenerName: String) = s"oxf.$servletOrFilterOrListenerName"

  private def enabled(ctx: ServletContext, paramPrefix: String): Boolean = {
    val enabledParam = s"$paramPrefix.$EnabledParamPart"
    ctx.getInitParameter(enabledParam).trimAllToOpt.forall(_ == "true")
  }

  private case class RegistrationResult(missingInitParams: Set[String] = Set.empty)

  private object RegistrationResult {
    def merged(RegistrationResults: Seq[RegistrationResult]): RegistrationResult =
      RegistrationResult(missingInitParams = RegistrationResults.flatMap(_.missingInitParams).toSet)
  }

  private def registerServlet(
    ctx                : ServletContext,
    servletName        : String,
    servletClass       : Class[_ <: JavaxOrJakartaServlet],
    initParamPrefix    : String,
    mandatoryInitParams: Set[String]
  ): RegistrationResult = {
    val paramPrefix = this.paramPrefix(servletName)

    if (enabled(ctx, paramPrefix)) {
      // Dynamically register servlet
      val registration = ctx.addServlet(servletName, servletClass)

      // Servlet mapping
      val mappingParam            = s"$paramPrefix.$MappingParamPart"
      val missingMappingInitParam = Option(ctx.getInitParameter(mappingParam)) match {
        case Some(mapping) =>
          registration.addMapping(mapping)
          Set.empty[String]
        case None =>
          logger.error(s"Servlet '$servletName': missing mandatory init parameter '$mappingParam'")
          Set(mappingParam)
      }

      val registrationResult = copyInitParams(
        ctx                 = ctx,
        registration        = registration,
        paramPrefix         = paramPrefix,
        servletOrFilter     = "Servlet",
        servletOrFilterName = servletName,
        initParamPrefix     = initParamPrefix,
        mandatoryInitParams = mandatoryInitParams
      )

      RegistrationResult(missingInitParams = registrationResult.missingInitParams ++ missingMappingInitParam)
    } else {
      logger.info(s"Servlet '$servletName' is disabled")

      RegistrationResult()
    }
  }

  private def registerFilter(
    ctx                : ServletContext,
    filterName         : String,
    filterClass        : Class[_ <: JavaxOrJakartaFilter],
    dispatcherTypes    : Set[DispatcherType],
    initParamPrefix    : String,
    mandatoryInitParams: Set[String]
  ): RegistrationResult = {
    val paramPrefix = this.paramPrefix(filterName)

    if (enabled(ctx, paramPrefix)) {
      // Dynamically register filter
      val registration = ctx.addFilter(filterName, filterClass)

      // Servlet mapping
      val urlPatternParam         = s"$paramPrefix.$UrlPatternParamPart"
      val missingMappingInitParam = Option(ctx.getInitParameter(urlPatternParam)) match {
        case Some(urlPattern) =>
          // TODO: parse dispatcher types as well?
          registration.addMappingForUrlPatterns(dispatcherTypes, isMatchAfter = true, urlPattern)
          Set.empty[String]
        case None =>
          logger.error(s"Filter '$filterName': missing mandatory init parameter '$urlPatternParam'")
          Set(urlPatternParam)
      }

      val registrationResult = copyInitParams(
        ctx                 = ctx,
        registration        = registration,
        paramPrefix         = paramPrefix,
        servletOrFilter     = "Filter",
        servletOrFilterName = filterName,
        initParamPrefix     = initParamPrefix,
        mandatoryInitParams = mandatoryInitParams
      )

      RegistrationResult(missingInitParams = registrationResult.missingInitParams ++ missingMappingInitParam)
    } else {
      logger.info(s"Filter '$filterName' is disabled")

      RegistrationResult()
    }
  }

  private def registerListener(
    ctx          : ServletContext,
    listenerName : String,
    listenerClass: Class[_ <: ju.EventListener]
  ): Unit = {
    val paramPrefix = this.paramPrefix(listenerName)

    if (enabled(ctx, paramPrefix)) {
      // Dynamically register listener
      ctx.addListener(listenerClass)
    } else {
      logger.info(s"Listener '$listenerName' is disabled")
    }
  }

  private def copyInitParams(
    ctx                : ServletContext,
    registration       : ServletOrFilterRegistration,
    paramPrefix        : String,
    servletOrFilter    : String,
    servletOrFilterName: String,
    initParamPrefix    : String,
    mandatoryInitParams: Set[String]
  ): RegistrationResult = {
    // Copy context params to servlet/filter init params
    val contextParams         = ctx.getInitParameterNames.asScala.toSeq
    val paramPartsToFilterOut = Seq(EnabledParamPart, MappingParamPart, UrlPatternParamPart)
    val prefixesToFilterOut   = paramPartsToFilterOut.map(paramPart => s"$paramPrefix.$paramPart")

    val foundInitParams =
      contextParams
        .filter(_.startsWith(paramPrefix))
        .filterNot(param => prefixesToFilterOut.exists(prefix => param.startsWith(prefix)))
        .map { contextParamName =>
          val initParamName = initParamPrefix + contextParamName.substring(paramPrefix.length + 1) // Remove the dot as well
          val value         = ctx.getInitParameter(contextParamName)

          val success       = registration.setInitParameter(initParamName, value)

          if (success) {
            logger.info(s"$servletOrFilter '$servletOrFilterName': set init parameter '$initParamName' to '$value'")
          } else {
            logger.error(s"$servletOrFilter '$servletOrFilterName': failed to set init parameter '$initParamName'")
          }

          initParamName
    }

    val missingMandatoryInitParams = mandatoryInitParams -- foundInitParams

    // Log missing mandatory init params
    missingMandatoryInitParams.toSeq.sorted.foreach { param =>
      logger.error(s"$servletOrFilter '$servletOrFilterName': missing mandatory init parameter '$param'")
    }

    RegistrationResult(missingInitParams = missingMandatoryInitParams)
  }
}

