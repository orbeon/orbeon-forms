/**
 * Copyright (C) 2015 Orbeon, Inc.
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

import org.junit.Test
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.SequenceIterator
import org.orbeon.scaxon.XML._
import org.scalatest.junit.AssertionsForJUnit

class EmailTest extends DocumentTestBase with AssertionsForJUnit {

  val FormWithEmailControls = "oxf:/org/orbeon/oxf/fr/form-with-email-controls.xhtml"

  @Test def testEmailExtraction(): Unit = {

    val formDoc = readURLAsImmutableXMLDocument(FormWithEmailControls)

    val head     = formDoc.rootElement / (XH → "head") head
    val model    = head / XFModelTest head
    val instance = model descendant XFInstanceTest filter (_.id == "fr-form-instance") head
    val body     = formDoc.rootElement / (XH → "body") head

    val data =
      TransformerUtils.extractAsMutableDocument(instance child * head)

    def sequenceIteratorToStringList(it: SequenceIterator) =
      asScalaIterator(it).map(_.getStringValue).to[List]

    locally {

      val expectedForClassName = List(
        "fr-email-recipient"                → List("info+toplevel@orbeon.com"),
        "fr-email-subject"                  → List(),
        "fr-attachment"                     → List("attachment-13.bin", "attachment-14.bin"),
        "fr-attachment fr-email-attachment" → List("attachment-14.bin")
      )

      def valuesForClassName(className: String) =
        sequenceIteratorToStringList(
          searchHoldersForClassTopLevelOnly(
            body,
            data,
            className
          )
        )

      for ((className, expected) ← expectedForClassName)
        assert(expected === valuesForClassName(className))
    }

    locally {

      val expectedForClassName = List(
        "fr-email-recipient"                → List("info+0@orbeon.com", "info+1@orbeon.com", "info+2@orbeon.com"),
        "fr-email-subject"                  → List("Abc", "Def", "Ghi"),
        "fr-attachment"                     → List("attachment-10.bin", "attachment-11.bin"),
        "fr-attachment fr-email-attachment" → List("attachment-11.bin")
      )

      def valuesForClassName(className: String) =
        sequenceIteratorToStringList(
          searchHoldersForClassUseSectionTemplates(
            head,
            body,
            data,
            className
          )
        )

      for ((className, expected) ← expectedForClassName)
        assert(expected === valuesForClassName(className))
    }
  }
}
