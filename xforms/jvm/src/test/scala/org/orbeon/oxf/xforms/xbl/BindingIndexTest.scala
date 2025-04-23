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
package org.orbeon.oxf.xforms.xbl

import org.orbeon.css.CSSSelectorParser
import org.orbeon.css.CSSSelectorParser.Selector
import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.xbl.BindingIndex.DatatypeMatch
import org.orbeon.oxf.xml.dom.IOSupport
import org.orbeon.xml.NamespaceMapping
import org.scalatest.funspec.AnyFunSpecLike


class BindingIndexTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  case class TestBinding(
    selectors        : List[Selector],
    namespaceMapping : NamespaceMapping
  ) extends IndexableBinding {
    val path         : Option[String] = None
    val lastModified : Long           = -1L
    val datatypeOpt  : Option[QName]  = None
    val constraintOpt: Option[String] = None
  }

  val FooURI     = "http://orbeon.org/oxf/xml/foo"
  val Namespaces = NamespaceMapping(Map("foo" -> FooURI))

  val AllSelectors =
    CSSSelectorParser.parseSelectors(
      """
        foo|bar,
        foo|baz,
        [appearance ~= baz],
        [appearance =  gaga],
        [appearance ~= gaga],
        [appearance |= gaga],
        [appearance ^= gaga],
        [appearance $= gaga],
        [appearance *= gaga],
        foo|bar[appearance ~= baz],
        foo|bar[repeat = content],
        foo|bar[appearance ~= baz][repeat = content],
        foo|bar[repeat]
      """.trimAllToEmpty
    )

  val AllBindings =
    AllSelectors map (s => TestBinding(List(s), Namespaces))

  val (
      fooBarBinding                   ::
      fooBazBinding                   ::
      appearanceTokenBazBinding       ::
      appearanceIsGagaBinding         ::
      appearanceTokenGagaBinding      ::
      appearancePrefixGagaBinding     ::
      appearanceStartsWithGagaBinding ::
      appearanceEndsWithGagaBinding   ::
      appearanceContainsGagaBinding   ::
      fooBarAppearanceBazBinding      ::
      fooBarRepeatContent             ::
      fooBarAppearanceAndAtt          ::
      fooBarRepeat                    ::
      Nil
    ) = AllBindings

  def indexWithAllBindings = {
    var currentIndex: BindingIndex[IndexableBinding] = GlobalBindingIndex.Empty

    // We wrote the attribute bindings above from more specific to least specific, and the index prepends new
    // bindings as we index, so newer bindings are found first. To help with testing matching by attribute, we
    // index in reverse order, so that e.g. `[appearance ~= baz]` is found before `[appearance *= gaga]`.
    AllBindings.reverse foreach { binding =>
      currentIndex = BindingIndex.indexBinding(currentIndex, binding)
    }

    currentIndex
  }

  def parseXMLElemWithNamespaces(xmlElem: String): Element = {

    val namespacesString =
      Namespaces.mapping map { case (prefix, uri) => s"""xmlns:$prefix="$uri"""" } mkString " "

    val encapsulated =
      s"""<root $namespacesString>$xmlElem</root>"""

    IOSupport.readOrbeonDom(encapsulated).getRootElement.elements.head
  }

  def assertElemMatched(xmlElem: String, binding: IndexableBinding)(implicit index: BindingIndex[IndexableBinding]): Unit = {

    val elem = parseXMLElemWithNamespaces(xmlElem)
    val atts = elem.attributes map (a => a.getQName -> a.getValue)

    val found = BindingDescriptor.findMostSpecificBinding(elem.getQName, DatatypeMatch.Exclude, atts)

    it(s"must pass with `$xmlElem`") {
      assert(found.map(_._1).contains(binding))
    }
  }

  describe("Selector priority") {

    implicit val currentIndex: BindingIndex[IndexableBinding] = indexWithAllBindings

    assertElemMatched("""<foo:bar/>""",                                   fooBarBinding)
    assertElemMatched("""<foo:baz/>""",                                   fooBazBinding)
    assertElemMatched("""<foo:bar appearance="bar"/>""",                  fooBarBinding)
    assertElemMatched("""<foo:baz appearance="bar"/>""",                  fooBazBinding)
    assertElemMatched("""<foo:baz appearance="baz"/>""",                  appearanceTokenBazBinding)
    assertElemMatched("""<foo:baz appearance="fuzz baz toto"/>""",        appearanceTokenBazBinding)
    assertElemMatched("""<foo:bar appearance="baz"/>""",                  fooBarAppearanceBazBinding)
    assertElemMatched("""<foo:bar repeat="content"/>""",                  fooBarRepeatContent)
    assertElemMatched("""<foo:bar appearance="baz" repeat="content"/>""", fooBarAppearanceAndAtt)
    assertElemMatched("""<foo:bar repeat="true"/>""",                     fooBarRepeat)
  }

  describe("Matching by attribute") {

    implicit val currentIndex: BindingIndex[IndexableBinding] = indexWithAllBindings

    assertElemMatched("""<foo:bar appearance="gaga"/>""",           appearanceIsGagaBinding)
    assertElemMatched("""<foo:bar appearance="fuzz gaga toto"/>""", appearanceTokenGagaBinding)
    assertElemMatched("""<foo:bar appearance="gaga toto"/>""",      appearanceTokenGagaBinding)
    assertElemMatched("""<foo:bar appearance="fuzz gaga"/>""",      appearanceTokenGagaBinding)
    assertElemMatched("""<foo:bar appearance="gaga-en"/>""",        appearancePrefixGagaBinding)
    assertElemMatched("""<foo:bar appearance="gagaba"/>""",         appearanceStartsWithGagaBinding)
    assertElemMatched("""<foo:bar appearance="bagaga"/>""",         appearanceEndsWithGagaBinding)
    assertElemMatched("""<foo:bar appearance="bagagada"/>""",       appearanceContainsGagaBinding)
  }
}
