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

import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._


trait FormRunnerDocContext {

  def formDefinitionRootElem: NodeInfo

  lazy val modelElem             = FormRunner.getModelElem(formDefinitionRootElem)
  lazy val dataInstanceElem      = FormRunner.instanceElemFromModelElem(modelElem, Names.FormInstance).get
  lazy val metadataInstanceElem  = FormRunner.instanceElemFromModelElem(modelElem, Names.MetadataInstance).get
  lazy val resourcesInstanceElem = FormRunner.instanceElemFromModelElem(modelElem, Names.FormResources).get
  lazy val topLevelBindElem      = FormRunner.findTopLevelBindFromModelElem(modelElem)
  lazy val bodyElem              = FormRunner.getFormRunnerBodyElem(formDefinitionRootElem)

  lazy val dataRootElem          = dataInstanceElem      / * head
  lazy val metadataRootElem      = metadataInstanceElem  / * head
  lazy val resourcesRootElem     = resourcesInstanceElem / * head
}
