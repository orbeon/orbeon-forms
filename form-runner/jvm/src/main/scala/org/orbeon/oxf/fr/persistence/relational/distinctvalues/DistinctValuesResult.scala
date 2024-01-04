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
package org.orbeon.oxf.fr.persistence.relational.distinctvalues

import org.orbeon.oxf.fr.persistence.relational.RelationalUtils.Logger
import org.orbeon.oxf.fr.persistence.relational.distinctvalues.adt.DistinctValues
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.scaxon.NodeConversions

trait DistinctValuesResult {

  def outputResult(
    distinctValues: DistinctValues,
    receiver      : XMLReceiver
  ): Unit = {

    val controlsElems =
      <distinct-values>{
        List(
          distinctValues.controlValues.map { controlValues =>
            <control path={controlValues.path}>{
                controlValues.distinctValues.map { value =>
                  <value>{value}</value>
                }
            }</control>
          },
          distinctValues.createdByValues.toSeq.map { values =>
            <created-by>{
              values.map { value =>
                <value>{value}</value>
              }
            }</created-by>
          },
          distinctValues.lastModifiedByValues.toSeq.map { values =>
            <last-modified-by>{
              values.map { value =>
                <value>{value}</value>
              }
            }</last-modified-by>
          },
          distinctValues.workflowStageValues.toSeq.map { values =>
            <workflow-stage>{
              values.map { value =>
                <value>{value}</value>
              }
            }</workflow-stage>
          }
        ).flatten
      }</distinct-values>

    if (Logger.debugEnabled)
      Logger.logDebug("distinct values result", controlsElems.toString)

    NodeConversions.elemToSAX(controlsElems, receiver)
  }
}
