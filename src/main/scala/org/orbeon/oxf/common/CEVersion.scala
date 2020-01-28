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

import java.util.concurrent.ConcurrentHashMap
import java.{util => ju}

class CEVersion extends Version {

  import CEVersion._
  import Version._

  // Feature is disallowed
  def requirePEFeature(featureName: String): Unit =
    throw new OXFException(featureMessage(featureName))

  def isPEFeatureEnabled(featureRequested: Boolean, featureName: String): Boolean = {
    // Just warn the first time
    if (featureRequested && ! WarnedFeatures.containsKey(featureName)) {
      logger.warn(featureMessage(featureName))
      WarnedFeatures.put(featureName, ())
    }
    false
  }
}

private object CEVersion {
  val WarnedFeatures: ju.Map[String, Unit] = new ConcurrentHashMap[String, Unit]
  def featureMessage(featureName: String) =
    s"Feature is not enabled in this version of the product: $featureName"
}
