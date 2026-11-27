/**
 * Copyright (C) 2026 Orbeon, Inc.
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

import io.circe.{Json, JsonObject, parser}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions
import org.orbeon.scaxon.SimplePath.*

import scala.xml.Elem


object RemoteServersEditorUI {

  import Private.*

  //@XPathFunction
  def isValidRemoteServersJson(jsonString: String): Boolean = {
    val trimmed = Option(jsonString).getOrElse("").trimAllToEmpty
    if (trimmed.isEmpty) {
      true
    } else {
      parseRemoteServers(trimmed).isDefined
    }
  }

  //@XPathFunction
  def formatRemoteServersJson(jsonString: String): String = {
    val raw     = Option(jsonString).getOrElse("")
    val trimmed = raw.trimAllToEmpty
    if (trimmed.isEmpty) {
      "[]"
    } else {
      parser.parse(trimmed) match {
        case Right(json) if json.isArray => json.spaces2
        case _                           => raw
      }
    }
  }

  //@XPathFunction
  def jsonToUiXml(jsonString: String): NodeInfo = {
    val entries = parseRemoteServers(Option(jsonString).getOrElse("").trimAllToEmpty).getOrElse(Nil)
    NodeConversions.elemToNodeInfo(entriesToUiElem(entries))
  }

  //@XPathFunction
  def uiXmlToJson(serversElOrNull: NodeInfo): String =
    Option(serversElOrNull) match {
      case None =>
        "[]"
      case Some(serversEl) =>
        val jsonEntries =
          (serversEl / ServerElemName).toList.map { serverEl =>
            val name      = serverEl.attValue(NameAttr).trimAllToEmpty
            val label     = serverEl.attValue(LabelAttr).trimAllToEmpty
            val url       = serverEl.attValue(UrlAttr).trimAllToEmpty
            val uiAccess  = serverEl.attValue(UiAccessAttr) != "false"
            val apiAccess = serverEl.attValue(ApiAccessAttr) == "true"

            val fields = Vector.newBuilder[(String, Json)]
            if (name.nonEmpty)  fields += NameAttr  -> Json.fromString(name)
            if (label.nonEmpty) fields += LabelAttr -> Json.fromString(label)
            fields += UrlAttr -> Json.fromString(url)
            if (! uiAccess)     fields += UiAccessAttr  -> Json.fromBoolean(false)
            if (apiAccess)      fields += ApiAccessAttr -> Json.fromBoolean(true)

            Json.fromJsonObject(JsonObject.fromIterable(fields.result()))
          }

        if (jsonEntries.isEmpty) "[]"
        else Json.fromValues(jsonEntries).spaces2
    }

  private object Private {

    val ServerElemName = "server"
    val NameAttr       = "name"
    val LabelAttr      = "label"
    val UrlAttr        = "url"
    val UiAccessAttr   = "ui-access"
    val ApiAccessAttr  = "api-access"

    case class RemoteServerEntry(
      name     : String,
      label    : String,
      url      : String,
      uiAccess : Boolean,
      apiAccess: Boolean
    )

    def parseRemoteServers(trimmedJson: String): Option[List[RemoteServerEntry]] =
      if (trimmedJson.isEmpty) {
        Some(Nil)
      } else {
        for {
          json     <- parser.parse(trimmedJson).toOption
          elements <- json.asArray
          entries  <- parseEntries(elements.toList)
          if hasNoDuplicateNames(entries)
        } yield entries
      }

    def parseEntries(elements: List[Json]): Option[List[RemoteServerEntry]] = {
      val builder = List.newBuilder[RemoteServerEntry]
      val iter    = elements.iterator
      while (iter.hasNext) {
        parseEntry(iter.next()) match {
          case Some(entry) => builder += entry
          case None        => return None
        }
      }
      Some(builder.result())
    }

    def parseEntry(json: Json): Option[RemoteServerEntry] =
      for {
        obj       <- json.asObject
        url       <- obj(UrlAttr).flatMap(_.asString).flatMap(_.trimAllToOpt)
        name      <- optionalString(obj, NameAttr)
        label     <- optionalString(obj, LabelAttr)
        uiAccess  <- optionalBoolean(obj, UiAccessAttr, default = true)
        apiAccess <- optionalBoolean(obj, ApiAccessAttr, default = false)
      } yield RemoteServerEntry(
        name      = name,
        label     = label,
        url       = url,
        uiAccess  = uiAccess,
        apiAccess = apiAccess
      )

    def optionalString(obj: JsonObject, key: String): Option[String] =
      obj(key) match {
        case None                 => Some("")
        case Some(j) if j.isNull  => Some("")
        case Some(j)              => j.asString.map(_.trimAllToEmpty)
      }

    def optionalBoolean(obj: JsonObject, key: String, default: Boolean): Option[Boolean] =
      obj(key) match {
        case None                 => Some(default)
        case Some(j) if j.isNull  => Some(default)
        case Some(j)              => j.asBoolean
      }

    def hasNoDuplicateNames(entries: List[RemoteServerEntry]): Boolean = {
      val nonBlankNames = entries.map(_.name).filter(_.nonEmpty)
      nonBlankNames.distinct.size == nonBlankNames.size
    }

    def entriesToUiElem(entries: List[RemoteServerEntry]): Elem =
      <editor>
        <servers>{
          entries.map { e =>
            <server
              name={e.name}
              label={e.label}
              url={e.url}
              ui-access={e.uiAccess.toString}
              api-access={e.apiAccess.toString}/>
          }
        }</servers>
      </editor>
  }
}
