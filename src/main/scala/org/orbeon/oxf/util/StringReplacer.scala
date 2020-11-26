/**
 *  Copyright (C) 2013 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util

import org.orbeon.oxf.util.StringUtils._
import spray.json._

import scala.util.control.NonFatal

// Factory for a string replacement function configured by a JSON map.
//
// - The configuration is in JSON format and must be a top-level map of String -> String.
// - A blank string is allowed to specify "no mapping".
// - Filtering applies all the transformations one after the other.
object StringReplacer extends Logging {
  // Read a filter configuration and return a filter function
  // If there was an error processing the configuration, log and return
  def apply(json: String)(implicit logger: IndentedLogger): String => String = {
    val mapping = json.trimAllToOpt match {
      case Some(nonEmptyJSON) =>
        try
          nonEmptyJSON.parseJson match {
            case JsObject(fields) =>
              fields collect {
                case (k, v: JsString) => k -> v.value
                case _ => throw new IllegalArgumentException
              }
            case _ => throw new IllegalArgumentException
          }
        catch {
          case NonFatal(t) =>
            warn("configuration must be a JSON map of String -> String", Seq("JSON" -> json))
            Map()
        }
      case None => Map()
    }

    // Do the replacement of all mappings one after the other
    s => mapping.foldLeft(s){ case (prev, (k, v)) => prev.replaceAllLiterally(k, v) }
  }
}