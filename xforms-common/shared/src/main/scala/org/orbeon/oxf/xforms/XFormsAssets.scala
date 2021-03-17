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
package org.orbeon.oxf.xforms

import org.orbeon.dom.QName
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.PathUtils._


case class XFormsAssets(css: List[AssetPath], js: List[AssetPath], xbl: Set[QName])

case class AssetPath(full: String, minOpt: Option[String]) {
  def assetPath(tryMin: Boolean): String =
    if (tryMin) minOpt getOrElse full else full
}

object AssetPath {

  def apply(full: String, hasMin: Boolean): AssetPath =
    AssetPath(full, hasMin option minFromFull(full))

  def minFromFull(full: String): String =
    findExtension(full) match {
      case Some(ext) => full.substring(0, full.length - ext.length - 1) + ".min." + ext
      case None      => throw new IllegalArgumentException
    }
}

object XFormsAssetPaths {

  val XFormServerPrefix         = "/xforms-server/"
  val DynamicResourcesPath      = XFormServerPrefix + "dynamic/"

  val DynamicResourceRegex      = (DynamicResourcesPath + "(.+)").r

  val FormDynamicResourcesPath  = XFormServerPrefix + "form/dynamic/"
  val FormDynamicResourcesRegex = s"$FormDynamicResourcesPath(.+).js".r

  val FormStaticResourcesPath   = XFormServerPrefix + "form/static/"
  val FormStaticResourcesRegex  =  s"$FormStaticResourcesPath(.+).js".r

  val BaselineResourceRegex     = (XFormServerPrefix + "baseline\\.(js|css)").r
}