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
package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.FormRunnerCommon.*
import org.orbeon.oxf.fr.XMLNames.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*


object SchemaOps {

  def findSchema(inDoc: NodeInfo): Option[NodeInfo] =
    frc.getModelElem(inDoc) / XSSchemaTest headOption

  def findSchemaNamespace(inDoc: NodeInfo): Option[String] =
    for {
      schema          <- findSchema(inDoc)
      targetNamespace <- schema attValueOpt "targetNamespace"
    } yield
      targetNamespace

  def findSchemaPrefix(inDoc: NodeInfo): Option[String] =
    for {
      schema          <- findSchema(inDoc)
      targetNamespace <- findSchemaNamespace(inDoc)
      prefix          <- schema.nonEmptyPrefixesForURI(targetNamespace).sorted.headOption
    } yield
      prefix
}
