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

import org.orbeon.oxf.util.StringUtils._

object RunMode {

  val RunModeProperty = "oxf.run-mode"

  val ProdRunMode = "prod"
  val DevRunMode = "dev"

  val DefaultRunMode = ProdRunMode

  // Return the web app's run mode
  def getRunMode(contextParams: Map[String, String]) =
    contextParams.get(RunModeProperty) flatMap trimAllToOpt getOrElse DefaultRunMode
}
