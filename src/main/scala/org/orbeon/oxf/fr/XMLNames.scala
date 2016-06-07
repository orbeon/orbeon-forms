/**
 * Copyright (C) 2014 Orbeon, Inc.
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
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.scaxon.XML.Test

object XMLNames {

  val FR = "http://orbeon.org/oxf/xml/form-runner"

  val XBLXBLTest            : Test = XBL → "xbl"
  val XBLBindingTest        : Test = XBL → "binding"
  val XBLTemplateTest       : Test = XBL → "template"
  val XBLImplementationTest : Test = XBL → "implementation"

  val FRBodyTest            : Test = FR → "body"

  val FRGridTest            : Test = FR → "grid"
  val FRSectionTest         : Test = FR → "section"

  val XFModelTest           : Test = XF → "model"
  val XFInstanceTest        : Test = XF → "instance"
  val XFBindTest            : Test = XF → "bind"
  val XFGroupTest           : Test = XF → "group"

  val FRMetadata            : Test = FR → "metadata"
  val FRItemsetId           : Test = FR → "itemsetid"
  val FRItemsetMap          : Test = FR → "itemsetmap"

  val FRContainerTest = FRSectionTest || FRGridTest

  val FRNamespace     = Namespace("fr", FR)
  val ItemsetIdQName  = new QName("itemsetid",  FRNamespace)
  val ItemsetMapQName = new QName("itemsetmap", FRNamespace)
}
