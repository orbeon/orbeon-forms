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

import org.orbeon.oxf.fr.persistence.relational.distinctvalues.adt.{ControlValues, DistinctValues, MetadataValues}
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.scaxon.NodeConversions


trait DistinctValuesResult {

  def outputResult(
    distinctValues: DistinctValues,
    receiver      : XMLReceiver
  )(implicit
    indentedLogger: IndentedLogger
  ): Unit = {

    val distinctValuesElem =
      <distinct-values>{
        distinctValues.values.map {
          case ControlValues(path, distinctValues) =>
            <query path={path}>{
              distinctValues.map { value =>
                <value>{value}</value>
              }
            }</query>

          case MetadataValues(metadata, distinctValues) =>
            <query metadata={metadata.string}>{
              distinctValues.map { value =>
                <value>{value}</value>
              }
            }</query>
        }
      }</distinct-values>

    debug(s"distinct values result: ${distinctValuesElem.toString}")

    NodeConversions.elemToSAX(distinctValuesElem, receiver)
  }
}
