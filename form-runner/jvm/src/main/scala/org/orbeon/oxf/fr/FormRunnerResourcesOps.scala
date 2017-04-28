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
import org.orbeon.oxf.util.IOUtils.useAndClose
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.XML._


trait FormRunnerResourcesOps {

  def allResources(resources: NodeInfo) : Seq[NodeInfo] = resources child "resource"
  def allLangs    (resources: NodeInfo) : Seq[String]   = allResources(resources) attValue "*:lang"

  def resourcesInstanceRootOpt(inDoc: NodeInfo): Option[NodeInfo] = inlineInstanceRootElement(inDoc, FormResources)

  def resourcesInstanceDocFromUrlOpt(inDoc: NodeInfo): Option[NodeInfo] =
    instanceElement(inDoc, FormResources) flatMap
      (_.attValueOpt("src"))              map
      readUrlAsImmutableXmlDocument       map
      (_.rootElement)

  def allLangsWithResources(resources: NodeInfo): Seq[(String, NodeInfo)] =
    allLangs(resources) zip allResources(resources)

  // Same as above but doesn't require a Form Builder context
  // NOTE: Support an entirely missing resources instance (for tests).
  def findResourceHoldersWithLangUseDoc(inDoc: NodeInfo, controlName: String): Seq[(String, NodeInfo)] =
    resourcesInstanceRootOpt(inDoc)                 orElse
      resourcesInstanceDocFromUrlOpt(inDoc)         map
      (findResourceHoldersWithLang(controlName, _)) getOrElse
      Nil


  // Find control resource holders with their language
  def findResourceHoldersWithLang(controlName: String, resources: NodeInfo): Seq[(String, NodeInfo)] =
    for {
      (lang, resource) ← allLangsWithResources(resources)
      holder           ← resource child controlName headOption // there *should* be only one
    } yield
      (lang, holder)

  private def readUrlAsImmutableXmlDocument(url: String) =
    useAndClose(URLFactory.createURL(url).openStream()) { is ⇒
      TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, null, false, false)
    }
}

object FormRunnerResourcesOps extends FormRunnerResourcesOps