/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.saxon

import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon
import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.saxon.`type`.Type._
import org.orbeon.saxon.`type`.{BuiltInAtomicType, Type}
import org.orbeon.saxon.expr.StaticProperty._
import org.orbeon.saxon.function._

// TODO: Separate pure functions from this
trait IndependentFunctions extends OrbeonFunctionLibrary {

  // Define in early definition of subclass
  val IndependentFunctionsNS: Seq[String]

  Namespace(IndependentFunctionsNS) {

    Fun("digest", classOf[Digest], op = 0, min = 2, STRING, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("hmac", classOf[Hmac], op = 0, min = 3, STRING, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("random", classOf[Random], op = 0, min = 0, NUMERIC, EXACTLY_ONE,
      Arg(BOOLEAN, ALLOWS_ZERO_OR_ONE)
    )

    // TODO: Split this out into separate trait
    Fun("get-portlet-mode", classOf[GetPortletMode], op = 0, min = 0, STRING, ALLOWS_ONE)

    // TODO: Split this out into separate trait
    Fun("get-window-state", classOf[GetWindowState], op = 0, min = 0, STRING, ALLOWS_ONE)

    // TODO: Split this out into separate trait
    Fun("get-session-attribute", classOf[GetSessionAttribute], op = 0, min = 1, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    // TODO: Split this out into separate trait
    Fun("set-session-attribute", classOf[SetSessionAttribute], op = 0, min = 2, ITEM_TYPE, ALLOWS_ZERO,
      Arg(STRING, EXACTLY_ONE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    // TODO: Split this out into separate trait
    Fun("get-request-attribute", classOf[GetRequestAttribute], op = 0, min = 1, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    // TODO: Split this out into separate trait
    Fun("set-request-attribute", classOf[SetRequestAttribute], op = 0, min = 2, ITEM_TYPE, ALLOWS_ZERO,
      Arg(STRING, EXACTLY_ONE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    // TODO: Split this out into separate trait
    Fun("username"                   , classOf[Username],              op = 0, min = 0, STRING, ALLOWS_ZERO_OR_ONE)
    Fun("get-remote-user"            , classOf[Username],              op = 0, min = 0, STRING, ALLOWS_ZERO_OR_ONE)
    Fun("user-group"                 , classOf[UserGroup],             op = 0, min = 0, STRING, ALLOWS_ZERO_OR_ONE)
    Fun("user-roles"                 , classOf[UserRoles],             op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE)
    Fun("user-organizations"         , classOf[UserOrganizations],     op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE)

    // TODO: Split this out into separate trait
    Fun("user-ancestor-organizations", classOf[AncestorOrganizations], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    // TODO: Split this out into separate trait
    Fun("is-user-in-role", classOf[IsUserInRole], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("call-xpl", classOf[CallXPL], op = 0, min = 4, NODE_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_MORE),
      Arg(NODE_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(STRING, ALLOWS_ZERO_OR_MORE)
    )

    Fun("evaluate", classOf[saxon.functions.Evaluate], op = saxon.functions.Evaluate.EVALUATE, min = 1, max = 10, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("serialize", classOf[saxon.functions.Serialize], op = 0, min = 2, STRING, EXACTLY_ONE,
      Arg(NODE_TYPE, ALLOWS_ZERO_OR_ONE),
      Arg(ITEM_TYPE, EXACTLY_ONE)
    )

    Fun("property", classOf[Property], op = 0, min = 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("properties-start-with", classOf[PropertiesStartsWith], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("decode-iso9075-14", classOf[DecodeISO9075], op = 0, min = 1, STRING, ALLOWS_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("encode-iso9075-14", classOf[EncodeISO9075], op = 0, min = 1, STRING, ALLOWS_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("doc-base64", classOf[DocBase64], op = DocBase64.DOC_BASE64, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, ALLOWS_ZERO_OR_ONE)
    )

    Fun("doc-base64-available", classOf[DocBase64], op = DocBase64.DOC_BASE64_AVAILABLE, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("rewrite-resource-uri", classOf[RewriteResourceURI], op = 0, min = 1, STRING, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("rewrite-service-uri", classOf[RewriteServiceURI], op = 0, min = 1, STRING, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("has-class", classOf[HasClass], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(NODE_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("classes", classOf[Classes], op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(NODE_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("split", classOf[Split], op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, ALLOWS_ZERO_OR_MORE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("trim", classOf[Trim], op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, ALLOWS_ZERO_OR_MORE)
    )

    Fun("is-blank", classOf[IsBlank], op = 0, min = 0, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, ALLOWS_ZERO_OR_MORE)
    )

    Fun("non-blank", classOf[NonBlank], op = 0, min = 0, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, ALLOWS_ZERO_OR_MORE)
    )

    Fun("forall", classOf[Forall], op = 0, min = 2, BOOLEAN, EXACTLY_ONE,
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, EXACTLY_ONE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("exists", classOf[Exists], op = 0, min = 2, BOOLEAN, EXACTLY_ONE,
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, EXACTLY_ONE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("image-metadata", classOf[ImageMetadata], op = 0, min = 2, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(ITEM_TYPE, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("json-to-xml", classOf[JsonStringToXml], op = 0, min = 0, NODE_TYPE, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, ALLOWS_ZERO_OR_ONE)
    )

    Fun("xml-to-json", classOf[XmlToJsonString], op = 0, min = 0, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(NODE_TYPE, ALLOWS_ZERO_OR_ONE)
    )

    Fun("create-document", classOf[CreateDocument], op = 0, min = 0, Type.NODE_TYPE, EXACTLY_ONE)

    Fun("process-template", classOf[ProcessTemplate], op = 0, min = 2, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE) // `map(*)`
    )

    Fun("mutable-document", classOf[XXFormsMutableDocument], op = 0, min = 1, Type.NODE_TYPE, EXACTLY_ONE,
      Arg(Type.NODE_TYPE, EXACTLY_ONE)
    )
  }
}
