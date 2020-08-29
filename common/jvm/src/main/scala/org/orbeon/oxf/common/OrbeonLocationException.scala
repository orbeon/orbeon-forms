/**
 * Copyright (C) 2013 Orbeon, Inc.
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

import collection.JavaConverters._
import javax.xml.transform.TransformerException
import org.orbeon.errorified.Exceptions
import org.orbeon.oxf.xml.dom.LocationData
import org.xml.sax.SAXParseException
import scala.util.control.NonFatal

object OrbeonLocationException {
  /**
   * Return all the LocationData information for that throwable
   */
  def getAllLocationData(throwable: Throwable): List[LocationData] =
    Exceptions.causesIterator(throwable).toList.reverse flatMap getLocationData

  def jGetAllLocationData(throwable: Throwable) =
    getAllLocationData(throwable).asJava

  // NOTE: We used to attempt to get a "better" LocationData instead of the first one. It's unclear that this made
  // much sense. See:
  // https://github.com/orbeon/orbeon-forms/blob/75f6fc832a76eb66b125d01a36df52489af8c79f/src/main/java/org/orbeon/oxf/common/ValidationException.java#L63
  def getRootLocationData(throwable: Throwable) =
    getAllLocationData(throwable).headOption

  private def getLocationData(throwable: Throwable): List[LocationData] =
    throwable match {
      case t: ValidationException =>
        t.allLocationData
      case te: TransformerException =>
        te.getException match {
          case null | NonFatal(_) => // unclear logic
            Option(te.getLocator) map
            { l => new LocationData(l.getSystemId, l.getLineNumber, l.getColumnNumber) } toList
          case _ =>
            Nil
        }
      case t: SAXParseException =>
        List(new LocationData(t.getSystemId, t.getLineNumber, t.getColumnNumber))
      case _ =>
        Nil
    }

  def wrapException(throwable: Throwable, locationData: LocationData): ValidationException =
    throwable match {
      case t: ValidationException => Option(locationData) foreach t.addLocationData; t
      case t                      => new ValidationException(t, locationData)
    }
}
