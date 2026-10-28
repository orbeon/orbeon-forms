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
package org.orbeon.saxon.function

import org.orbeon.oxf.csv.Converter
import org.orbeon.oxf.xml.{DefaultFunctionSupport, DependsOnContextItemIfSingleArgumentMissing}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.saxon.value.{BooleanValue, StringValue}
import org.orbeon.scaxon.Implicits.*

import scala.util.Try

class CsvStringToXml extends DefaultFunctionSupport with DependsOnContextItemIfSingleArgumentMissing with AddToPathMap {
  override def evaluateItem(xpathContext: XPathContext): DocumentInfo = {
    implicit val xpc: XPathContext = xpathContext
    val stringOpt   = stringArgumentOrContextOpt(0)
    val header      = parseHeaderOption(1).getOrElse(true)
    val documentOpt = stringOpt.flatMap(s => Try(Converter.csvStringToXmlDoc(s, header = header)).toOption)
    documentOpt.orNull
  }

  private def parseHeaderOption(i: Int)(implicit xpathContext: XPathContext): Option[Boolean] =
    arguments.lift(i).flatMap { argExpr =>
      Option(argExpr.evaluateItem(xpathContext)).map {
        case b: BooleanValue => b.getBooleanValue
        case s: StringValue =>
          s.getStringValue.trim.toLowerCase match {
            case "present" | "true" | "yes" | "1" => true
            case "absent"  | "false" | "no" | "0" => false
            case _                                => true
          }
        case _ =>
          effectiveBooleanValue(argExpr)
      }
    }
}

class XmlToCsvString extends DefaultFunctionSupport with DependsOnContextItemIfSingleArgumentMissing {
  override def evaluateItem(xpathContext: XPathContext): StringValue = {
    implicit val xpc: XPathContext = xpathContext
    val header = parseHeaderOption(1).getOrElse(true)
    val resOpt = itemArgumentOrContextOpt(0).collect {
      case node: NodeInfo => Converter.xmlToCsvString(node, header = header)
    }
    resOpt.map(new StringValue(_)).orNull
  }

  private def parseHeaderOption(i: Int)(implicit xpathContext: XPathContext): Option[Boolean] =
    arguments.lift(i).flatMap { argExpr =>
      Option(argExpr.evaluateItem(xpathContext)).map {
        case b: BooleanValue => b.getBooleanValue
        case s: StringValue =>
          s.getStringValue.trim.toLowerCase match {
            case "present" | "true" | "yes" | "1" => true
            case "absent"  | "false" | "no" | "0" => false
            case _                                => true
          }
        case _ =>
          effectiveBooleanValue(argExpr)
      }
    }
}
