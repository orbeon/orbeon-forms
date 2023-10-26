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
package org.orbeon.oxf.fr.persistence.relational.distinctcontrolvalues

import org.orbeon.oxf.fr.persistence.relational.RelationalUtils.Logger
import org.orbeon.oxf.fr.persistence.relational.distinctcontrolvalues.adt.ControlValues
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.scaxon.NodeConversions

trait DistinctControlValuesResult {

  def outputResult(
    allControlValues : List[ControlValues],
    receiver         : XMLReceiver
  ): Unit = {

    val controlsElems =
      <controls>{
        allControlValues.map { controlValues =>
          <control path={controlValues.path}>
            <values>{
              controlValues.distinctValues.map { value =>
                <value>{value}</value>
              }
            }</values>
          </control>
        }
      }</controls>

    if (Logger.debugEnabled)
      Logger.logDebug("distinct control values result", controlsElems.toString)

    NodeConversions.elemToSAX(controlsElems, receiver)
  }
}
