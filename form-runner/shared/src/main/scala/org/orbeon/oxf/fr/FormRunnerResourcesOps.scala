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

import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.Names.FormResources
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._


trait FormRunnerResourcesOps {

  //@XPathFunction
  def allLangs    (resourcesRootElem: NodeInfo): Seq[String]   = allResources(resourcesRootElem) attValue "*:lang"
  def allResources(resourcesRootElem: NodeInfo): Seq[NodeInfo] = resourcesRootElem child "resource"

  //@XPathFunction
  def resourcesInstanceRootElemOpt(inDoc: NodeInfo): Option[NodeInfo] = inlineInstanceRootElem(inDoc, FormResources)

  def resourcesInstanceDocFromUrlOpt(inDoc: NodeInfo): Option[NodeInfo] =
    instanceElem(inDoc, FormResources) flatMap
      (_.attValueOpt("src"))              map
      readUrlAsImmutableXmlDocument       map
      (_.rootElement)

  def allLangsWithResources(resourcesRootElem: NodeInfo): Seq[(String, NodeInfo)] =
    allLangs(resourcesRootElem) zip allResources(resourcesRootElem)

  // Same as above but doesn't require a Form Builder context
  // NOTE: Support an entirely missing resources instance (for tests).
  // TODO: Migrate to `findResourceHoldersWithLangUseDocUseContext`.
  def findResourceHoldersWithLangUseDoc(inDoc: NodeInfo, controlName: String): Seq[(String, NodeInfo)] =
    resourcesInstanceRootElemOpt(inDoc)             orElse
      resourcesInstanceDocFromUrlOpt(inDoc)         map
      (findResourceHoldersWithLang(controlName, _)) getOrElse
      Nil

  def findResourceHoldersWithLangUseDocUseContext(
    controlName : String)(implicit
    ctx         : FormRunnerDocContext
  ): Seq[(String, NodeInfo)] =
    findResourceHoldersWithLang(controlName, ctx.resourcesRootElem)

  // Find control resource holders with their language
  def findResourceHoldersWithLang(controlName: String, resourcesRootElem: NodeInfo): Seq[(String, NodeInfo)] =
    for {
      (lang, resource) <- allLangsWithResources(resourcesRootElem)
      holder           <- resource child controlName headOption // there *should* be only one
    } yield
      (lang, holder)

  private def readUrlAsImmutableXmlDocument(url: String) =
    useAndClose(URLFactory.createURL(url).openStream()) { is =>
      TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, null, false, false)
    }
}

object FormRunnerResourcesOps extends FormRunnerResourcesOps