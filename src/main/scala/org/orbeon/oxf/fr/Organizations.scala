/**
  * Copyright (C) 2016 Orbeon, Inc.
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

import java.net.{URLDecoder, URLEncoder}

import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.webapp.ExternalContext
import spray.json._

object Organizations {

  private object Symbols {
    val Username      = "username"
    val Groups        = "groups"
    val Roles         = "roles"
    val Organizations = "organizations"
    val Organization  = "organization"
    val Name          = "name"
    val Value         = "value"
    val Parameters    = "parameters"
  }

  private def maybeEncodeJsStringContent(s: String) = {
    val urlEncoded    = URLEncoder.encode(s, ExternalContext.StandardHeaderCharacterEncoding)
    val spacesEncoded = s.replaceAllLiterally(" ", "%20")

    if (urlEncoded.replaceAllLiterally("+", "%20") != spacesEncoded)
      urlEncoded.replaceAllLiterally("+", " ")
    else
      s
  }

  private def maybeDecodeJsStringContent(s: String) =
    URLDecoder.decode(s, ExternalContext.StandardHeaderCharacterEncoding)

  def serializeCredentials(
    username        : String,
    userRoles       : List[UserRole],
    groups          : List[String],
    organizations   : List[Organization],
    encodeForHeader : Boolean
  ) = {

    val encode: String ⇒ String =
      if (encodeForHeader) maybeEncodeJsStringContent else identity

    def serializeOrganization(organization: Organization) =
      JsArray(organization.levels.iterator map encode map JsString.apply toVector)

    def serializeRole(userRole: UserRole) =
      userRole match {
        case SimpleRole(roleName) ⇒
          JsObject(
            Symbols.Name → JsString(encode(roleName))
          )
        case ParametrizedRole(roleName, organizationName) ⇒
          JsObject(
            Symbols.Name       → JsString(encode(roleName)),
            Symbols.Parameters → JsArray(
              JsObject(
                Symbols.Name  → JsString(Symbols.Organization),
                Symbols.Value → JsString(encode(organizationName))
              )
            )
          )
      }

    JsObject(
      Symbols.Username      → JsString(encode(username)),
      Symbols.Groups        → JsArray(groups        map encode map JsString.apply toVector),
      Symbols.Roles         → JsArray(userRoles     map serializeRole             toVector),
      Symbols.Organizations → JsArray(organizations map serializeOrganization     toVector)
    )
  }

  def parseCredentials(
    credentials     : String,
    decodeForHeader : Boolean
  ): Option[Credentials] =
    if (credentials.isBlank) {
      None
    } else {

      val decode: String ⇒ String =
        if (decodeForHeader) maybeDecodeJsStringContent else identity

      (credentials.parseJson: @unchecked) match {
        case JsObject(nameValues) ⇒

          val usernameOpt = nameValues.get(Symbols.Username) map {
            case JsString(value) ⇒ decode(value)
            case _ ⇒ throw new IllegalArgumentException
          }

          val rolesOpt =
            (nameValues.get(Symbols.Roles): @unchecked) map {
              case JsArray(roles) ⇒
                (roles: @unchecked) map {
                  case JsObject(roleNameValues) ⇒

                    val roleName = (roleNameValues.get(Symbols.Name): @unchecked) match {
                      case Some(JsString(value)) ⇒ decode(value)
                    }

                    val params = (roleNameValues.get(Symbols.Parameters): @unchecked) map {
                      case JsArray(parameters) ⇒
                        (parameters: @unchecked) map {
                          case JsObject(parameterNameValues) ⇒
                            val parameterMap =
                              (parameterNameValues: @unchecked) map {
                                case (name, JsString(value)) ⇒ name → value
                              }

                            ((parameterMap.get(Symbols.Name), parameterMap.get(Symbols.Value)): @unchecked) match {
                              case (Some(name), Some(value)) ⇒ name → value
                            }
                        }
                    }

                    (params: @unchecked) match {
                      case Some(params) if params.toMap.contains(Symbols.Organization)  ⇒
                        ParametrizedRole(decode(roleName), decode(params.toMap.apply(Symbols.Organization)))
                      case None ⇒
                        SimpleRole(decode(roleName))
                    }
                }
            }

          val organizationsOpt =
            (nameValues.get(Symbols.Organizations): @unchecked) map {
              case JsArray(hierarchies) ⇒
                (hierarchies: @unchecked) map {
                  case JsArray(orgNames) ⇒

                    val names =
                      (orgNames: @unchecked) map {
                        case JsString(orgName) ⇒ decode(orgName)
                      }

                    Organization(names.to[List])
                }
            }

          val groupsOpt =
            (nameValues.get(Symbols.Groups): @unchecked) map {
              case JsArray(groups) ⇒
                (groups: @unchecked) map {
                  case JsString(value) ⇒ decode(value)
                }
            }

          usernameOpt map { username ⇒
            Credentials(
              username     = username,
              group        = groupsOpt flatMap (_.headOption),
              roles        = rolesOpt map (_.to[List]) getOrElse Nil,
              organization = organizationsOpt flatMap (_.headOption)
            )
          }
      }
    }

}
