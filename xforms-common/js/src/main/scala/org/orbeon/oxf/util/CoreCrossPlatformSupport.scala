/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.oxf.util

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.xml.XMLConstants.XS_NMTOKENS_QNAME

import scala.jdk.CollectionConverters._


object CoreCrossPlatformSupport extends CoreCrossPlatformSupportTrait {

  type FileItemType = Unit // TODO

  def isPE: Boolean = true

  private val hexDigits = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

  // This is probably not as good as the server side code based on `SecureRandom`
  def randomHexId: String =
    0 until 40 map (_ => hexDigits((Math.random() * 16).floor.toInt)) mkString

  def getApplicationResourceVersion: Option[String] = None // TODO: CHECK

  def properties: PropertySet =
    PropertySet(
      List(
        (
          null,
          "oxf.xforms.logging.debug",
          XS_NMTOKENS_QNAME,
          Set(
            "document",
            "model",
            "submission",
            "submission-details",
            "control",
            "control-tree",
            "event",
            "action",
            "analysis",
            "server",
            "server-body",
            "html",
            "analysis",
            "resources"
          ).asJava
        )
      )
    )

  def externalContext: ExternalContext = ??? // TODO: unused on JS side
}
