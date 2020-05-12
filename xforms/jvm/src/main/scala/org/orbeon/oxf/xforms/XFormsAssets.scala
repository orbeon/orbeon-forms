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

import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._
import spray.json._
import scala.collection.compat._

case class XFormsAssets(css: List[AssetPath], js: List[AssetPath])

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

object XFormsAssets {

  val AssetsBaselineProperty = "oxf.xforms.assets.baseline"

  def updateAssets(assets: XFormsAssets, excludesProp: String, updatesProp: String): XFormsAssets = {

    def maybeRemoveOne(assets: XFormsAssets, path: String): XFormsAssets =
      assets.copy(css = assets.css.filter(_.full != path), js = assets.js.filter(_.full != path))

    def maybeAddOne(assets: XFormsAssets, path: String): XFormsAssets = {

      val isCss = path.endsWith(".css")

      val existingItems  = if (isCss) assets.css else assets.js

      (! existingItems.exists(_.full == path)) option (existingItems :+ AssetPath(path, None)) match {
        case Some(newItems) if isCss => assets.copy(css = newItems)
        case Some(newItems)          => assets.copy(js = newItems)
        case None                    => assets
      }
    }

    def maybeAddOrRemoveOne(assets: XFormsAssets, update: String): XFormsAssets =
      if (update.startsWith("-"))
        maybeRemoveOne(assets, update.tail)
      else if (update.startsWith("+"))
        maybeAddOne(assets, update.tail)
      else
        maybeAddOne(assets, update)

    val allUpdates =
      (excludesProp.splitTo[List]() map ("-" + _)) ::: updatesProp.splitTo[List]()

    allUpdates.foldLeft(assets)(maybeAddOrRemoveOne)
  }

  def fromJsonString(json: String): XFormsAssets =
    fromJson(json.parseJson)

  def fromJSONProperty: XFormsAssets =
    Properties.instance.getPropertySet
      .getPropertyOrThrow(AssetsBaselineProperty)
      .associatedValue(v => fromJsonString(v.value.toString))

  private def fromJson(json: JsValue): XFormsAssets = {

    def collectFullMin(key: String, fields: Map[String, JsValue]): Vector[AssetPath] =
      fields.get(key) match {
        case Some(JsArray(values)) =>
          values collect { case JsObject(fields) =>
            val full   = fields.get("full") collect { case JsString(v)  => v } getOrElse (throw new IllegalArgumentException)
            val hasMin = fields.get("min")  collect { case JsBoolean(v) => v } getOrElse false

            AssetPath(full, hasMin)
          }
        case _ => throw new IllegalArgumentException
      }

    json match {
      case JsObject(fields) =>

        val css = collectFullMin("css", fields)
        val js  = collectFullMin("js",  fields)

        XFormsAssets(css.to(List), js.to(List))

      case _ => throw new IllegalArgumentException
    }
  }
}
