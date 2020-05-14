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
import org.orbeon.dom.Element
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.{Dom4j, NamespaceMapping}
import org.scalatest.funspec.AnyFunSpec

class BindingIndexTest extends AnyFunSpec {

  case class TestBinding(
    selectors        : List[Selector],
    namespaceMapping : NamespaceMapping
  ) extends IndexableBinding {
    val path         = None
    val lastModified = -1L
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
      fooBarRepeat                    ::
      Nil
    ) = AllBindings

  def indexWithAllBindings = {
    var currentIndex: BindingIndex[IndexableBinding] = GlobalBindingIndex.Empty

    // We wrote the attribute bindings above from more specific to least specific, and the index prepends new
    // bindings as we index, so newer bindings are found first. To help with testing matching by attribute, we
    // index in reverse order, so that e.g. [appearance ~= baz] is found before [appearance *= gaga].
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

    Dom4j.elements(Dom4jUtils.readDom4j(encapsulated).getRootElement).head
  }

  def assertElemMatched(index: BindingIndex[IndexableBinding], xmlElem: String, binding: IndexableBinding) = {

    val elem = parseXMLElemWithNamespaces(xmlElem)
    val atts = Dom4j.attributes(elem) map (a => a.getQName -> a.getValue)

    val found = BindingIndex.findMostSpecificBinding(index, elem.getQName, atts)

    it(s"must pass with `$xmlElem`") {
      assert(Some(binding) === (found map (_._1)))
    }
  }

  describe("Selector priority") {

    val currentIndex = indexWithAllBindings

    assertElemMatched(currentIndex, """<foo:bar/>""",                            fooBarBinding)
    assertElemMatched(currentIndex, """<foo:baz/>""",                            fooBazBinding)
    assertElemMatched(currentIndex, """<foo:bar appearance="bar"/>""",           fooBarBinding)
    assertElemMatched(currentIndex, """<foo:baz appearance="bar"/>""",           fooBazBinding)
    assertElemMatched(currentIndex, """<foo:baz appearance="baz"/>""",           appearanceTokenBazBinding)
    assertElemMatched(currentIndex, """<foo:baz appearance="fuzz baz toto"/>""", appearanceTokenBazBinding)
    assertElemMatched(currentIndex, """<foo:bar appearance="baz"/>""",           fooBarAppearanceBazBinding)
    assertElemMatched(currentIndex, """<foo:bar repeat="content"/>""",           fooBarRepeatContent)
    assertElemMatched(currentIndex, """<foo:bar repeat="true"/>""",              fooBarRepeat)
  }

  describe("Matching by attribute") {

    val currentIndex = indexWithAllBindings

    assertElemMatched(currentIndex, """<foo:bar appearance="gaga"/>""",           appearanceIsGagaBinding)
    assertElemMatched(currentIndex, """<foo:bar appearance="fuzz gaga toto"/>""", appearanceTokenGagaBinding)
    assertElemMatched(currentIndex, """<foo:bar appearance="gaga toto"/>""",      appearanceTokenGagaBinding)
    assertElemMatched(currentIndex, """<foo:bar appearance="fuzz gaga"/>""",      appearanceTokenGagaBinding)
    assertElemMatched(currentIndex, """<foo:bar appearance="gaga-en"/>""",        appearancePrefixGagaBinding)
    assertElemMatched(currentIndex, """<foo:bar appearance="gagaba"/>""",         appearanceStartsWithGagaBinding)
    assertElemMatched(currentIndex, """<foo:bar appearance="bagaga"/>""",         appearanceEndsWithGagaBinding)
    assertElemMatched(currentIndex, """<foo:bar appearance="bagagada"/>""",       appearanceContainsGagaBinding)
  }
}
