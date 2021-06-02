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

import io.circe.{Json, JsonObject, parser}
import org.orbeon.dom.QName
import org.orbeon.oxf.properties.{Property, PropertySet}
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.dom.Extensions

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

  def fromJsonProperty(propertySet: PropertySet): XFormsAssets = {
    val prop = propertySet.getPropertyOrThrow(AssetsBaselineProperty)
    prop.associatedValue(v => fromJsonString(v.value.toString, prop.namespaces))
  }

  // Public for tests
  def fromJsonString(json: String, namespaces: Map[String, String]): XFormsAssets =
    (parser.parse(json) map (fromJson(_, namespaces))).toTry.getOrElse(throw new IllegalArgumentException(json))

  private def fromJson(json: Json, namespaces: Map[String, String]): XFormsAssets = {

    def collectFullMin(key: String, jsonObject: JsonObject): Vector[AssetPath] =
      jsonObject(key) match {
        case Some(jsonArray) =>

          import io.circe.generic.auto._

          case class FullMin(full: String, min: Boolean)

          jsonArray.as[Vector[FullMin]] match {
            case Right(list)   => list map { case FullMin(full, min) => AssetPath(full, min) }
            case Left(failure) => throw failure
          }

        case _ => throw new IllegalArgumentException
      }

    def collectXbl(key: String, jsonObject: JsonObject): Vector[QName] =
      jsonObject(key) match {
        case Some(jsonArray) =>
          jsonArray.as[Vector[String]] match {
            case Right(list)   =>
              list map { lexicalQName =>
                Extensions.resolveQName(namespaces.get, lexicalQName, unprefixedIsNoNamespace = true) getOrElse
                  (throw new IllegalArgumentException)
              }
            case Left(failure) => throw failure
          }
        case _ => Vector.empty
      }

    json.asObject match {
      case Some(jsonObject) =>

        val css = collectFullMin("css", jsonObject)
        val js  = collectFullMin("js",  jsonObject)
        val xbl = collectXbl    ("xbl", jsonObject)

        XFormsAssets(css.to(List), js.to(List), xbl.toSet)

      case _ => throw new IllegalArgumentException
    }
  }
}
