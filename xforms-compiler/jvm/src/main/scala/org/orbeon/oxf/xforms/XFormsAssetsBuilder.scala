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
import org.orbeon.oxf.properties.Property
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.dom.Extensions
import spray.json._

import scala.collection.compat._


object XFormsAssetsBuilder {

  val AssetsBaselineProperty = "oxf.xforms.assets.baseline"

  def updateAssets(
    assets       : XFormsAssets,
    excludesProp : Option[String],
    updatesProp  : Option[Property]
  ): XFormsAssets = {

    def qNameFromString(s: String): QName =
      updatesProp
        .map(_.namespaces.get _)
        .flatMap(Extensions.resolveQName(_, s, unprefixedIsNoNamespace = true))
        .getOrElse(throw new IllegalArgumentException(s"can't resolve QName `$s`"))

    def isCss(update: String) = update.endsWith(".css")
    def isXbl(update: String) = ! update.startsWith("/")

    def maybeRemoveOne(assets: XFormsAssets, update: String): XFormsAssets =
      if (isXbl(update))
        assets.copy(xbl = assets.xbl - qNameFromString(update))
      else if (isCss(update))
        assets.copy(css = assets.css.filter(_.full != update))
      else
        assets.copy(js = assets.js.filter(_.full != update))

    def maybeAddOne(assets: XFormsAssets, update: String): XFormsAssets =
      if (isXbl(update)) {
        assets.copy(xbl = assets.xbl + qNameFromString(update))
      } else {

        val isCss         = update.endsWith(".css")
        val existingItems = if (isCss) assets.css else assets.js

        (! existingItems.exists(_.full == update)) option (existingItems :+ AssetPath(update, None)) match {
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
      excludesProp.toList.flatMap(_.splitTo[List]() map ("-" + _)) ::: // translate to "remove" updates
      updatesProp.toList.flatMap(_.value.toString.splitTo[List]())

    allUpdates.foldLeft(assets)(maybeAddOrRemoveOne)
  }

  def fromJsonProperty: XFormsAssets = {

    val prop =
      CoreCrossPlatformSupport.properties.getPropertyOrThrow(AssetsBaselineProperty)

    prop.associatedValue(v => fromJsonString(v.value.toString, prop.namespaces))
  }

  // Public for tests
  def fromJsonString(json: String, namespaces: Map[String, String]): XFormsAssets =
    fromJson(json.parseJson, namespaces)

  private def fromJson(json: JsValue, namespaces: Map[String, String]): XFormsAssets = {

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

    def collectXbl(key: String, fields: Map[String, JsValue]): Vector[QName] =
      fields.get(key) match {
        case Some(JsArray(values)) =>
          values collect { case JsString(value) =>
            Extensions.resolveQName(namespaces.get, value, unprefixedIsNoNamespace = true)
              .getOrElse(throw new IllegalArgumentException)
          }
        case _ => Vector.empty
      }

    json match {
      case JsObject(fields) =>

        val css = collectFullMin("css", fields)
        val js  = collectFullMin("js",  fields)
        val xbl = collectXbl("xbl",  fields)

        XFormsAssets(css.to(List), js.to(List), xbl.toSet)

      case _ => throw new IllegalArgumentException
    }
  }
}
