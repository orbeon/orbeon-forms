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

import org.orbeon.oxf.fr.FormRunnerCommon._
import org.orbeon.oxf.fr.Names.FormResources
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsCrossPlatformSupport

import java.net.URI


trait FormRunnerResourcesOps {

  //@XPathFunction
  def allLangs    (resourcesRootElem: NodeInfo): collection.Seq[String]   = allResources(resourcesRootElem) attValue "*:lang"
  def allResources(resourcesRootElem: NodeInfo): collection.Seq[NodeInfo] = resourcesRootElem child "resource"

  //@XPathFunction
  def resourcesInstanceRootElemOpt(inDoc: NodeInfo): Option[NodeInfo] = frc.inlineInstanceRootElem(inDoc, FormResources)

  def allLangsWithResources(resourcesRootElem: NodeInfo): collection.Seq[(String, NodeInfo)] =
    allLangs(resourcesRootElem) zip allResources(resourcesRootElem)

  def formResourcesInGivenLangOrFirst(formResourcesRootElem: NodeInfo, lang: String): NodeInfo =
    allResources(formResourcesRootElem).find(_.attValue("*:lang") == lang).getOrElse(allResources(formResourcesRootElem).head)

  // Same as above but doesn't require a Form Builder context
  // NOTE: Support an entirely missing resources instance (for tests).
  // TODO: Migrate to `findResourceHoldersWithLangUseDocUseContext`.
  def findResourceHoldersWithLangUseDoc(inDoc: NodeInfo, controlName: String): collection.Seq[(String, NodeInfo)] =
    resourcesInstanceRootElemOpt(inDoc)             orElse
      resourcesInstanceDocFromUrlOpt(inDoc)         map
      (findResourceHoldersWithLang(controlName, _)) getOrElse
      Nil

  def findResourceHoldersWithLangUseDocUseContext(
    controlName : String)(implicit
    ctx         : FormRunnerDocContext
  ): collection.Seq[(String, NodeInfo)] =
    findResourceHoldersWithLang(controlName, ctx.resourcesRootElem)

  // Find control resource holders with their language
  def findResourceHoldersWithLang(controlName: String, resourcesRootElem: NodeInfo): collection.Seq[(String, NodeInfo)] =
    for {
      (lang, resource) <- allLangsWithResources(resourcesRootElem)
      holder           <- resource child controlName headOption // there *should* be only one
    } yield
      (lang, holder)

  // Support for `<xf:instance id="" src=""/>`, only for Form Builder's Summary page
  private def resourcesInstanceDocFromUrlOpt(inDoc: NodeInfo): Option[NodeInfo] =
    frc.instanceElem(inDoc, FormResources)           flatMap
      (_.attValueOpt("src"))                         map
      URI.create                                     map
      XFormsCrossPlatformSupport.readTinyTreeFromUrl map
      (_.rootElement)
}

object FormRunnerResourcesOps extends FormRunnerResourcesOps