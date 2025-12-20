/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.FormRunnerCommon.*
import org.orbeon.oxf.fr.Names.FormResources
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*


trait FormRunnerResourcesOps {

  //@XPathFunction
  def allLangs    (resourcesRootElem: NodeInfo): collection.Seq[String] = allResources(resourcesRootElem) attValue "*:lang"
  def allResources(resourcesRootElem: NodeInfo): NodeColl               = resourcesRootElem child "resource"

  //@XPathFunction
  def resourcesInstanceRootElemOpt(inDoc: NodeInfo): Option[NodeInfo] = frc.inlineInstanceRootElem(inDoc, FormResources)

  def allLangsWithResources(resourcesRootElem: NodeInfo): collection.Seq[(String, NodeInfo)] =
    allLangs(resourcesRootElem) zip allResources(resourcesRootElem)

  def formResourcesInGivenLangOrFirst(formResourcesRootElem: NodeInfo, lang: String): NodeInfo =
    allResources(formResourcesRootElem).find(_.attValue("*:lang") == lang).getOrElse(allResources(formResourcesRootElem).head)

  def findResourceHoldersWithLangUseDocUseContext(
    controlName : String
  )(implicit
    ctx         : FormRunnerDocContext
  ): collection.Seq[(String, NodeInfo)] =
    ctx.resourcesRootElemOpt.toList.flatMap(findResourceHoldersWithLang(controlName, _))

  // Find control resource holders with their language
  def findResourceHoldersWithLang(controlName: String, resourcesRootElem: NodeInfo): collection.Seq[(String, NodeInfo)] =
    for {
      (lang, resource) <- allLangsWithResources(resourcesRootElem)
      holder           <- resource.firstChildOpt(controlName) // there *should* be only one
    } yield
      (lang, holder)
}

object FormRunnerResourcesOps extends FormRunnerResourcesOps