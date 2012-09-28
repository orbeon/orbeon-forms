/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.common

import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.XPathDependencies

// Product version information
abstract class Version {

    import Version._

    def checkPEFeature(featureName: String)
    def isPEFeatureEnabled(featureRequested: Boolean, featureName: String): Boolean
    def createUIDependencies(containingDocument: XFormsContainingDocument): XPathDependencies

    override def toString = VersionString
}

object Version {

    private val PossibleEditions = Set("CE", "PE")

    val VersionNumber = "@RELEASE@"
    val Edition       = Option("@EDITION@") filter PossibleEditions getOrElse "CE"
    val VersionString = "Orbeon Forms " + VersionNumber + ' ' + Edition

    // For backward compatibility
    def getVersionString = VersionString

    val logger = LoggerFactory.createLogger(classOf[Version])

    // Create a Version instance using reflection
    lazy val instance: Version = {

        val versionClassName = "org.orbeon.oxf.common." + Edition + "Version"

        def logContextClassLoaderIssue[T](message: String): PartialFunction[Throwable, Option[T]] = {
            case throwable ⇒
                logger.info(message, throwable)
                None
        }

        def contextClassLoaderOpt =
            try Option(Thread.currentThread.getContextClassLoader)
            catch logContextClassLoaderIssue("Failed to obtain context ClassLoader")

        def fromContextClassLoaderOpt =
            contextClassLoaderOpt flatMap { classLoader ⇒
                try Some(classLoader.loadClass(versionClassName).asInstanceOf[Class[Version]])
                catch logContextClassLoaderIssue("Failed to load Version from context ClassLoader")
            }

        def fromName =
            Class.forName(versionClassName).asInstanceOf[Class[Version]]

        fromContextClassLoaderOpt getOrElse fromName newInstance
    }

    def isPE = Edition == "PE"
}
