/**
 * Copyright (C) 2019 Orbeon, Inc.
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

import org.orbeon.oxf.fr.FormRunnerCommon._
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._


trait FormRunnerDocContext {

  def formDefinitionRootElem: om.NodeInfo // Scala 3: trait parameter

  lazy val modelElem               : om.NodeInfo         = frc.getModelElem(formDefinitionRootElem)
  lazy val dataInstanceElem        : om.NodeInfo         = frc.instanceElemFromModelElem(modelElem, Names.FormInstance).get
  lazy val metadataInstanceElemOpt : Option[om.NodeInfo] = frc.instanceElemFromModelElem(modelElem, Names.MetadataInstance)
  lazy val resourcesInstanceElem   : om.NodeInfo         = frc.instanceElemFromModelElem(modelElem, Names.FormResources).get
  lazy val topLevelBindElem        : Option[om.NodeInfo] = frc.findTopLevelBindFromModelElem(modelElem)
  lazy val bodyElemOpt             : Option[om.NodeInfo] = frc.findFormRunnerBodyElem(formDefinitionRootElem)

  lazy val dataRootElem            : om.NodeInfo         = dataInstanceElem      / * head
  lazy val metadataRootElemOpt     : Option[om.NodeInfo] = metadataInstanceElemOpt.toList firstChildOpt *
  lazy val resourcesRootElem       : om.NodeInfo         = resourcesInstanceElem / * head

  def metadataInstanceElem         : om.NodeInfo         = metadataInstanceElemOpt.get
  def metadataRootElem             : om.NodeInfo         = metadataRootElemOpt.get
  def bodyElem                     : om.NodeInfo         = bodyElemOpt.get
}


class InDocFormRunnerDocContext(inDoc: om.NodeInfo) extends FormRunnerDocContext {
  val formDefinitionRootElem: om.NodeInfo = inDoc.rootElement
}