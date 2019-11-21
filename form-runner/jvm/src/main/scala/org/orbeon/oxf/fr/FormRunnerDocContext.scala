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


trait FormRunnerDocContext {
  def formDefinitionRootElem : NodeInfo
  def modelElem              : NodeInfo
  def dataInstanceElem       : NodeInfo
  def metadataInstanceElem   : NodeInfo
  def resourcesInstanceElem  : NodeInfo
  def topLevelBindElem       : Option[NodeInfo]
  def bodyElem               : NodeInfo
  def dataRootElem           : NodeInfo
  def metadataRootElem       : NodeInfo
  def resourcesRootElem      : NodeInfo
}
