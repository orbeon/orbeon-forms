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
package org.orbeon.oxf.xforms.model

import org.orbeon.connection.ConnectionResult
import org.orbeon.msv.grammar.Grammar
import org.orbeon.msv.reader.util.GrammarLoader
import org.orbeon.msv.reader.xmlschema.XMLSchemaReader
import org.orbeon.oxf.common.{OXFException, OrbeonLocationException}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.{Connection, CoreUtils, IndentedLogger, ResourceResolver}
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.schema.MSVGrammarReaderController
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.xml.sax.InputSource

import java.io.StringReader
import java.net.URI
import scala.util.control.NonFatal


trait XFormsModelSchemaValidatorPlatform {

  protected def loadCacheGrammar(containingDocument: XFormsContainingDocument, absoluteSchemaURL: String): Grammar =
    try {
      XFormsModelSchemaValidatorPlatform.cache.getOrElse(
        absoluteSchemaURL,
        {
          val is         = XFormsModelSchemaValidatorPlatform.resolveSchemaEntity(containingDocument, absoluteSchemaURL)
          val controller = new MSVGrammarReaderController(containingDocument, Some(absoluteSchemaURL))
          val grammar    = GrammarLoader.loadSchema(is, controller)
          XFormsModelSchemaValidatorPlatform.cache += absoluteSchemaURL -> grammar
          grammar
        }
      )
    } catch {
      case NonFatal(e) =>
        throw OrbeonLocationException.wrapException(e, XmlExtendedLocationData(absoluteSchemaURL, -1, -1, "loading schema from URI"))
    }

  protected def newXMLSchemaReader(containingDocument: XFormsContainingDocument): XMLSchemaReader = {
    val controller = new MSVGrammarReaderController(containingDocument, None)
    new XMLSchemaReader(controller)
  }
}

object XFormsModelSchemaValidatorPlatform {

  private var cache: Map[String, Grammar] = Map.empty

  private val XFormsTypesXsdUrl = "oxf:/org/orbeon/oxf/xforms/xforms-types.xsd"

  private val XFormsTypesXsd =
    """<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
      |           xmlns:xf="http://www.w3.org/2002/xforms"
      |           targetNamespace="http://www.w3.org/2002/xforms"
      |           elementFormDefault="qualified"
      |           attributeFormDefault="unqualified">
      |
      |    <xs:simpleType name="card-number">
      |        <xs:restriction base="xs:string">
      |            <xs:minLength value="12"/>
      |            <xs:maxLength value="19"/>
      |            <xs:pattern value="[0-9]+"/>
      |        </xs:restriction>
      |    </xs:simpleType>
      |
      |    <xs:simpleType name="dayTimeDuration">
      |        <xs:restriction base="xs:string">
      |            <xs:pattern value="([\-]?P([0-9]+D(T([0-9]+(H([0-9]+(M([0-9]+(\.[0-9]*)?S|\.[0-9]+S)?|(\.[0-9]*)?S)|(\.[0-9]*)?S)?|M([0-9]+(\.[0-9]*)?S|\.[0-9]+S)?|(\.[0-9]*)?S)|\.[0-9]+S))?|T([0-9]+(H([0-9]+(M([0-9]+(\.[0-9]*)?S|\.[0-9]+S)?|(\.[0-9]*)?S)|(\.[0-9]*)?S)?|M([0-9]+(\.[0-9]*)?S|\.[0-9]+S)?|(\.[0-9]*)?S)|\.[0-9]+S)))?"/>
      |        </xs:restriction>
      |    </xs:simpleType>
      |
      |    <xs:simpleType name="yearMonthDuration">
      |        <xs:restriction base="xs:string">
      |            <xs:pattern value="([\-]?P[0-9]+(Y([0-9]+M)?|M))?"/>
      |        </xs:restriction>
      |    </xs:simpleType>
      |
      |</xs:schema>""".stripMargin

  def resolveSchemaEntity(containingDocument: XFormsContainingDocument, urlString: String): InputSource =
    if (urlString == XFormsTypesXsdUrl) {
      new InputSource(new StringReader(XFormsTypesXsd)) |!> (_.setSystemId(urlString))
    } else if (containingDocument != null) {
      implicit val logger          : IndentedLogger           = containingDocument.getIndentedLogger("schema")
      implicit val externalContext : ExternalContext          = XFormsCrossPlatformSupport.externalContext
      implicit val resourceResolver: Option[ResourceResolver] = containingDocument.staticState.resourceResolverOpt

      val cxr =
        Connection.connectNow(
          method      = HttpMethod.GET,
          url         = URI.create(urlString),
          credentials = None,
          content     = None,
          headers     = Map.empty,
          loadState   = true,
          saveState   = true,
          logBody     = false
        )

      ConnectionResult.withSuccessConnection(cxr, closeOnSuccess = false) { is =>
        new InputSource(is) |!> (_.setSystemId(cxr.url))
      }
    } else {
      throw new OXFException(s"Cannot resolve schema URL `$urlString` without containing document")
    }
}
