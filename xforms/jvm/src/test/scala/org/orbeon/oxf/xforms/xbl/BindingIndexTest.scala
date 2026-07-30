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

import cats.syntax.option.*
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

  val Namespaces =
    NamespaceMapping(
      Map(
        "foo" -> "http://orbeon.org/oxf/xml/foo",
        "fr"  -> "http://orbeon.org/oxf/xml/fr"
      )
    )

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
        foo|bar[repeat],
        fr|grid[bind],
        fr|grid[repeat = content][bind]
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
    frGridBind                      ::
    frGridRepeatBind                ::
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

  def assertElemMatched(xmlElem: String, binding: Option[IndexableBinding])(implicit index: BindingIndex[IndexableBinding]): Unit = {

    val elem = parseXMLElemWithNamespaces(xmlElem)
    val atts = elem.attributes map (a => a.getQName -> a.getValue)

    val found = BindingDescriptor.findMostSpecificBinding(elem.getQName, DatatypeMatch.Exclude, atts)

    it(s"must pass with `$xmlElem`") {
      assert(found.map(_._1) == binding)
    }
  }

  describe("Selector priority") {

    implicit val currentIndex: BindingIndex[IndexableBinding] = indexWithAllBindings

    assertElemMatched("""<foo:bar/>""",                                   fooBarBinding.some)
    assertElemMatched("""<foo:baz/>""",                                   fooBazBinding.some)
    assertElemMatched("""<foo:bar appearance="bar"/>""",                  fooBarBinding.some)
    assertElemMatched("""<foo:baz appearance="bar"/>""",                  fooBazBinding.some)
    assertElemMatched("""<foo:baz appearance="baz"/>""",                  appearanceTokenBazBinding.some)
    assertElemMatched("""<foo:baz appearance="fuzz baz toto"/>""",        appearanceTokenBazBinding.some)
    assertElemMatched("""<foo:bar appearance="baz"/>""",                  fooBarAppearanceBazBinding.some)
    assertElemMatched("""<foo:bar repeat="content"/>""",                  fooBarRepeatContent.some)
    assertElemMatched("""<foo:bar appearance="baz" repeat="content"/>""", fooBarAppearanceAndAtt.some)
    assertElemMatched("""<foo:bar repeat="true"/>""",                     fooBarRepeat.some)

    assertElemMatched("""<fr:grid bind="my-bind"/>""",                    frGridBind.some)
    assertElemMatched("""<fr:grid repeat="gaga" bind="my-bind"/>""",      frGridBind.some)
    assertElemMatched("""<fr:grid repeat="content" bind="my-bind"/>""",   frGridRepeatBind.some)
    assertElemMatched("""<fr:grid repeat="content"/>""",                  None)
    assertElemMatched("""<fr:grid ref="my-ref"/>""",                      None)
  }

  describe("Matching by attribute") {

    implicit val currentIndex: BindingIndex[IndexableBinding] = indexWithAllBindings

    assertElemMatched("""<foo:bar appearance="gaga"/>""",           appearanceIsGagaBinding.some)
    assertElemMatched("""<foo:bar appearance="fuzz gaga toto"/>""", appearanceTokenGagaBinding.some)
    assertElemMatched("""<foo:bar appearance="gaga toto"/>""",      appearanceTokenGagaBinding.some)
    assertElemMatched("""<foo:bar appearance="fuzz gaga"/>""",      appearanceTokenGagaBinding.some)
    assertElemMatched("""<foo:bar appearance="gaga-en"/>""",        appearancePrefixGagaBinding.some)
    assertElemMatched("""<foo:bar appearance="gagaba"/>""",         appearanceStartsWithGagaBinding.some)
    assertElemMatched("""<foo:bar appearance="bagaga"/>""",         appearanceEndsWithGagaBinding.some)
    assertElemMatched("""<foo:bar appearance="bagagada"/>""",       appearanceContainsGagaBinding.some)
  }
}
