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
package org.orbeon.oxf.fr.persistence.relational.rest

import org.junit.Test
import org.orbeon.oxf.test.{ResourceManagerTestBase, XMLSupport}
import org.orbeon.xforms.XFormsCrossPlatformSupport.readTinyTreeFromUrl
import org.scalatestplus.junit.AssertionsForJUnit

import java.net.URI
import scala.collection.immutable.Seq
import scala.collection.mutable
import scala.collection.compat._


class FlatViewTest extends ResourceManagerTestBase with XMLSupport with AssertionsForJUnit {

  @Test def fixupDuplicatesTest(): Unit = {

    val values   = List("foo", "foo", "bar", "foo", "baz", "bar", "qux")
    val expected = List("foo", "fo1", "bar", "fo2", "baz", "ba1", "qu1")

    def fixupDuplicates(values: Iterator[String], initialValues: Seq[String], maxLength: Int): Iterator[String] = {
      val seen = mutable.HashSet[String](initialValues: _*)
      values map (v => FlatView.resolveDuplicate(v, maxLength)(seen))
    }

    val result = fixupDuplicates(values.iterator, List("qux"), values map (_.length) max)

    assert(expected === result.to(List))
  }

  @Test def extractPathsColsTest(): Unit = {

    val expectedForDocuments = List(
      "oxf:/org/orbeon/oxf/fr/form-with-itemsets.xhtml"             -> List(
        "section-1/dropdown"                                                   -> "SECTION_1_DROPDOWN",
        "section-1/radios"                                                     -> "SECTION_1_RADIOS"
      ),
      "oxf:/org/orbeon/oxf/fr/template-for-repeated-sections.xhtml" -> List(
        "my-section/my-input"                                                  -> "MY_SECTION_MY_INPUT",
        "other-section/other-input"                                            -> "OTHER_SECTION_OTHER_INPUT"
      ),
      "oxf:/org/orbeon/oxf/fr/form-with-long-control-names.xhtml"   -> List(
        "my-first-section/my-input"                                            -> "MY_FIRST_SECTION_MY_INPUT",
        "section-with-long-name/my-control-with-a-pretty-long-name"            -> "SECTION_WITH_L_MY_CONTROL_WITH",
        "section-with-long-name/my-control-with-a-pretty-long-name-too"        -> "SECTION_WITH_L_MY_CONTROL_WIT1",
        "section-with-long-name-too/short"                                     -> "SECTION_WITH_LONG_NAME_T_SHORT",
        "section-with-long-name-too/my-control-with-a-pretty-long-name-really" -> "SECTION_WITH_L_MY_CONTROL_WIT2"
      ),
      "oxf:/org/orbeon/oxf/fr/form-using-section-template.xhtml"    -> List(
        "top-section-a/form-section-b/form-field-a"                            -> "TOP_SECTI_FORM_SECT_FORM_FIELD",
        "top-section-a/form-section-b/form-field-b"                            -> "TOP_SECTI_FORM_SECT_FORM_FIEL1",
        "top-section-a/lib-section-a/lib-field-a"                              -> "TOP_SECTI_LIB_SECTIO_LIB_FIELD",
        "top-section-a/lib-section-a/lib-field-b"                              -> "TOP_SECTI_LIB_SECTIO_LIB_FIEL1"
      )
    )

    for ((url, expected) <- expectedForDocuments)
      assert(expected === FlatView.extractPathsCols(readTinyTreeFromUrl(URI.create(url))))
  }

  @Test def xmlToSQLIdTest(): Unit = {

    val expectedForValues = List(
      "FIRST_NAME"     -> "first-name",
      "FIRST___NAME"   -> "___first___name___",
      "0FIRST_NAME0"   -> "0first-name0",
      "FIRSTNAME"      -> "first$n%a.m/e "
    )

    for ((expected, xml) <- expectedForValues)
      assert(expected === FlatView.xmlToSQLId(xml))
  }

  @Test def fitValuesTest(): Unit = {

    val expectedForValues = List(
      ("bob_smi", "bob",    "smith"),
      ("smi_bob", "smith",  "bob"),
      ("a_longe", "a",      "longer"),
      ("longe_a", "longer", "a")
    )

    for ((expected, left, right) <- expectedForValues)
      assert(expected === FlatView.joinParts(List(left, right), 7))
  }
}
