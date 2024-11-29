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
import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.test.{ResourceManagerTestBase, XMLSupport}
import org.orbeon.xforms.XFormsCrossPlatformSupport.readTinyTreeFromUrl
import org.scalatestplus.junit.AssertionsForJUnit

import java.net.URI
import scala.collection.mutable


class FlatViewTest extends ResourceManagerTestBase with XMLSupport with AssertionsForJUnit {

  @Test def fixupDuplicatesTest(): Unit = {

    val values   = List("foo", "foo", "bar", "foo", "baz", "bar", "qux")
    val expected = List("foo", "fo1", "bar", "fo2", "baz", "ba1", "qu1")

    def fixupDuplicates(values: Iterator[String], initialValues: Seq[String], maxLength: Int): Iterator[String] = {
      val seen = mutable.HashSet[String](initialValues*)
      values map (v => FlatView.resolveDuplicate(v, maxLength)(seen))
    }

    val result = fixupDuplicates(values.iterator, List("qux"), values map (_.length) max)

    assert(expected === result.to(List))
  }

  @Test def deduplicatedColumnNamesTest(): Unit = {

    val expectedForDocuments = List(
      "oxf:/org/orbeon/oxf/fr/form-with-itemsets.xhtml"             -> List(
        "section-1/dropdown"                                                   -> "section_1_dropdown",
        "section-1/radios"                                                     -> "section_1_radios"
      ),
      "oxf:/org/orbeon/oxf/fr/template-for-repeated-sections.xhtml" -> List(
        "my-section/my-input"                                                  -> "my_section_my_input",
        "other-section/other-input"                                            -> "other_section_other_input"
      ),
      "oxf:/org/orbeon/oxf/fr/form-with-long-control-names.xhtml"   -> List(
        "my-first-section/my-input"                                            -> "my_first_section_my_input",
        "section-with-long-name/my-control-with-a-pretty-long-name"            -> "section_with_l_my_control_with",
        "section-with-long-name/my-control-with-a-pretty-long-name-too"        -> "section_with_l_my_control_wit1",
        "section-with-long-name-too/short"                                     -> "section_with_long_name_t_short",
        "section-with-long-name-too/my-control-with-a-pretty-long-name-really" -> "section_with_l_my_control_wit2"
      ),
      "oxf:/org/orbeon/oxf/fr/form-using-section-template.xhtml"    -> List(
        "top-section-a/form-section-b/form-field-a"                            -> "top_secti_form_sect_form_field",
        "top-section-a/form-section-b/form-field-b"                            -> "top_secti_form_sect_form_fiel1",
        "top-section-a/lib-section-a/lib-field-a"                              -> "top_secti_lib_sectio_lib_field",
        "top-section-a/lib-section-a/lib-field-b"                              -> "top_secti_lib_sectio_lib_fiel1"
      )
    )

    for ((url, expected) <- expectedForDocuments) {

      val view = FlatView.views(
        readTinyTreeFromUrl(URI.create(url)),
        Provider.PostgreSQL,
        AppForm("app", "form"),
        version = 1
      ).head

      val actual = FlatView.deduplicatedColumnNames(
        view.controls.map(_.columnNamePath(prefixesInColumnNames = true)),
        maxIdentifierLength = FlatView.CompatibilityMaxIdentifierLength
      ).toList.map(kv => kv._1.mkString("/") -> kv._2).sortBy(_._1)

      assert(expected.sortBy(_._1) === actual)
    }
  }

  @Test def xmlToSQLIdTest(): Unit = {

    val expectedForValues = List(
      "first_name"     -> "first-name",
      "first___name"   -> "___first___name___",
      "0first_name0"   -> "0first-name0",
      "firstname"      -> "first$n%a.m/e "
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
