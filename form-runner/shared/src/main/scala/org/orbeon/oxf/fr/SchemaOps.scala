/**
 * Copyright (C) 2013 Orbeon, Inc.
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

import org.orbeon.dom.{Namespace, QName}
import org.orbeon.oxf.fr.FormRunnerCommon.*
import org.orbeon.oxf.fr.XMLNames.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xml.SaxonUtils.parseQName
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.{Namespaces, XFormsNames}

import scala.util.Try


object SchemaOps {

  case class SimpleTypeSummary(
    localName         : String,
    qualifiedName     : String,
    restrictionSummary: String
  )

  case class ControlSchemaTypeUsage(
    controlName: String,
    rawType    : String,
    resolvedOpt: Option[QName]
  )

  private val FBURI        : String = "http://orbeon.org/oxf/xml/form-builder"
  private val XXBLTypeQName: QName  = QName("type", Namespace("xxbl", Namespaces.XXBL))

  private val XSTypeElemTest: Test = (XS -> "simpleType") || (XS -> "complexType")
  private val BindElemTest  : Test = XFBindTest || (FBURI -> "bind")
  private val TypeChildTest : Test = (XF -> "type") || (FBURI -> "type")

  def findSchema(inDoc: NodeInfo): Option[NodeInfo] =
    frc.getModelElem(inDoc) / XSSchemaTest headOption

  def findSchemaNamespace(inDoc: NodeInfo): Option[String] =
    findSchema(inDoc).flatMap(findSchemaNamespaceForSchema)

  def findSchemaPrefix(inDoc: NodeInfo): Option[String] =
    findSchema(inDoc).flatMap(findSchemaPrefixForSchema)

  def findSchemaNamespaceForSchema(schema: NodeInfo): Option[String] =
    schema.attValueOpt("targetNamespace").flatMap(_.trimAllToOpt)

  def findSchemaPrefixForSchema(schema: NodeInfo): Option[String] =
    for {
      targetNamespace <- findSchemaNamespaceForSchema(schema)
      prefix          <- schema.nonEmptyPrefixesForURI(targetNamespace).sorted.headOption
    } yield
      prefix

  def findSimpleTypes(schema: NodeInfo): List[SimpleTypeSummary] = {
    val prefixOpt = findSchemaPrefixForSchema(schema)
    (schema / (XS -> "simpleType")).toList.flatMap { simpleTypeElem =>
      simpleTypeElem.attValueOpt("name").flatMap(_.trimAllToOpt).map { localName =>
        val qualifiedName = prefixOpt.map(p => s"$p:$localName").getOrElse(localName)
        SimpleTypeSummary(
          localName          = localName,
          qualifiedName      = qualifiedName,
          restrictionSummary = summarizeSimpleType(simpleTypeElem)
        )
      }
    }
  }

  private def summarizeSimpleType(simpleTypeElem: NodeInfo): String =
    (simpleTypeElem / (XS -> "restriction")).headOption match {
      case Some(restriction) =>
        val base        = restriction.attValueOpt("base").flatMap(_.trimAllToOpt).getOrElse("")
        val enumCount   = (restriction / (XS -> "enumeration")).size
        val hasPattern  = (restriction / (XS -> "pattern")).nonEmpty
        val lengthOpt   = (restriction / (XS -> "length")).headOption.flatMap(_.attValueOpt("value").flatMap(_.trimAllToOpt))
        val minLenOpt   = (restriction / (XS -> "minLength")).headOption.flatMap(_.attValueOpt("value").flatMap(_.trimAllToOpt))
        val maxLenOpt   = (restriction / (XS -> "maxLength")).headOption.flatMap(_.attValueOpt("value").flatMap(_.trimAllToOpt))
        val minIncOpt   = (restriction / (XS -> "minInclusive")).headOption.flatMap(_.attValueOpt("value").flatMap(_.trimAllToOpt))
        val maxIncOpt   = (restriction / (XS -> "maxInclusive")).headOption.flatMap(_.attValueOpt("value").flatMap(_.trimAllToOpt))

        val lengthDetailOpt =
          lengthOpt.map(l => s"len $l").orElse {
            (minLenOpt, maxLenOpt) match {
              case (Some(minL), Some(maxL)) => Some(s"len $minL..$maxL")
              case (Some(minL), None)       => Some(s"minLen $minL")
              case (None,       Some(maxL)) => Some(s"maxLen $maxL")
              case (None,       None)       => None
            }
          }

        val rangeDetailOpt =
          (minIncOpt, maxIncOpt) match {
            case (Some(minV), Some(maxV)) => Some(s"$minV..$maxV")
            case (Some(minV), None)       => Some(s">= $minV")
            case (None,       Some(maxV)) => Some(s"<= $maxV")
            case (None,       None)       => None
          }

        val facets =
          List(
            if (enumCount > 0) Some(s"enum: $enumCount") else None,
            rangeDetailOpt,
            lengthDetailOpt,
            if (hasPattern) Some("pattern") else None
          ).flatten

        if (facets.nonEmpty && base.nonEmpty)
          s"$base (${facets.mkString(", ")})"
        else if (base.nonEmpty)
          base
        else if (facets.nonEmpty)
          facets.mkString(", ")
        else
          "—"

      case None =>
        (simpleTypeElem / (XS -> "list")).headOption match {
          case Some(listElem) =>
            listElem.attValueOpt("itemType").flatMap(_.trimAllToOpt).map(t => s"list ($t)").getOrElse("list")
          case None =>
            (simpleTypeElem / (XS -> "union")).headOption match {
              case Some(unionElem) =>
                unionElem.attValueOpt("memberTypes").flatMap(_.trimAllToOpt).map(t => s"union ($t)").getOrElse("union")
              case None =>
                "—"
            }
        }
    }

  def findControlSchemaTypeUsages(inDoc: NodeInfo): List[ControlSchemaTypeUsage] = {
    val rootElem      = inDoc.rootElement
    implicit val ctx: FormRunnerDocContext = new FormRunnerDocContext {
      val formDefinitionRootElem: NodeInfo         = rootElem
      lazy val viewContainerElemOpt: Option[NodeInfo] = bodyElemOpt
      lazy val formInstanceElemOpt : Option[NodeInfo] =
        modelElemOpt.flatMap(frc.instanceElemFromModelElem(_, Names.FormInstance))
    }
    val allBinds      = (ctx.topLevelBindElemOpt.toList descendant BindElemTest).toList
    val docPrefixOpt  = findSchemaPrefix(rootElem)
    val docNsOpt      = findSchemaNamespace(rootElem)

    // Pre-collect inline `<xbl:binding element="ns|local" xxbl:type="..."/>` QNames if any
    val inlineTypedXblQNames: Set[URIQualifiedName] =
      (rootElem / XHHeadTest / XBLXBLTest / XBLBindingTest)
        .filter(_.attValueOpt(XXBLTypeQName).exists(_.nonAllBlank))
        .flatMap { bindingElem =>
          bindingElem.attValueOpt("element").flatMap(_.trimAllToOpt).flatMap { elemSelector =>
            val normalized = elemSelector.replace('|', ':')
            Try(bindingElem.resolveURIQualifiedName(normalized)).toOption
          }
        }
        .toSet

    allBinds.flatMap { bindElem =>
      val controlNameOpt =
        bindElem.attValueOpt("name")
          .flatMap(_.trimAllToOpt)
          .orElse(frc.controlNameFromIdOpt(bindElem.id))

      val rawTypes =
        (bindElem.attValueOpt("type").flatMap(_.trimAllToOpt).toList ++
          (bindElem / TypeChildTest).flatMap(_.stringValue.trimAllToOpt)).distinct

      for {
        controlName <- controlNameOpt.toList
        rawType     <- rawTypes
        resolvedOpt =
          Try(bindElem.resolveQName(rawType)).toOption.orElse {
            val (prefix, localName) = parseQName(rawType)
            if (prefix.nonEmpty && docPrefixOpt.contains(prefix))
              docNsOpt.map(uri => QName(localName, prefix, uri))
            else
              None
          }
        if ! resolvedOpt.exists(isBuiltInDatatype)
        if ! resolvedOpt.exists(isComponentDatatype(controlName, _, inlineTypedXblQNames))
      } yield
        ControlSchemaTypeUsage(
          controlName = controlName,
          rawType     = rawType,
          resolvedOpt = resolvedOpt
        )
    }
  }

  private def isBuiltInDatatype(qName: QName): Boolean = {
    val uri = qName.namespace.uri
    uri == XMLConstants.XSD_URI             ||
    uri == XFormsNames.XFORMS_NAMESPACE_URI ||
    uri == XFormsNames.XXFORMS_NAMESPACE_URI
  }

  private def isComponentDatatype(
    controlName         : String,
    qName               : QName,
    inlineTypedXblQNames: Set[URIQualifiedName]
  )(implicit
    ctx                 : FormRunnerDocContext
  ): Boolean = {
    val targetUqn = URIQualifiedName(qName.namespace.uri, qName.localName)
    inlineTypedXblQNames.contains(targetUqn) ||
      frc.findControlByName(controlName).exists(_.uriQualifiedName == targetUqn)
  }

  def isSchemaTypeDefinedIn(usage: ControlSchemaTypeUsage, schemaOpt: Option[NodeInfo]): Boolean =
    schemaOpt match {
      case None =>
        false
      case Some(schema) =>
        val schemaTargetNs = findSchemaNamespaceForSchema(schema).getOrElse("")
        val definedNames   =
          (schema / XSTypeElemTest)
            .flatMap(_.attValueOpt("name").flatMap(_.trimAllToOpt))
            .toSet

        usage.resolvedOpt match {
          case Some(qName) =>
            qName.namespace.uri == schemaTargetNs && definedNames.contains(qName.localName)
          case None =>
            val (prefix, localName) = parseQName(usage.rawType)
            prefix.nonEmpty &&
              findSchemaPrefixForSchema(schema).contains(prefix) &&
              definedNames.contains(localName)
        }
    }

  def findDanglingSchemaTypeUsages(
    inDoc             : NodeInfo,
    effectiveSchemaOpt: Option[NodeInfo]
  ): List[ControlSchemaTypeUsage] =
    findControlSchemaTypeUsages(inDoc).filterNot(isSchemaTypeDefinedIn(_, effectiveSchemaOpt))
}
