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

    val VersionNumber = "@RELEASE@"
    val Edition       = "@EDITION@"
    val VersionString = "Orbeon Forms " + VersionNumber + ' ' + Edition

    val logger = LoggerFactory.createLogger(classOf[Version])

    lazy val instance: Version =
        if (isPE) {
            // Create a PEVersion instance using reflection
            val versionClassName = "org.orbeon.oxf.common.PEVersion"

            def contextClassLoaderOpt =
                try Option(Thread.currentThread.getContextClassLoader)
                catch {
                    case e: Exception ⇒
                        logger.error("Failed calling getContextClassLoader(), trying Class.forName()")
                        None
                }

            def fromClassLoader =
                contextClassLoaderOpt flatMap { classLoader ⇒
                    try Some(classLoader.loadClass(versionClassName).asInstanceOf[Class[Version]])
                    catch {
                        case e: Exception ⇒ None
                    }
                }

            def fromName = Class.forName(versionClassName).asInstanceOf[Class[Version]]

            fromClassLoader getOrElse fromName newInstance
        } else
            // Just create a CEVersion instance
            new CEVersion

    def isPE = Edition == "PE"
}
