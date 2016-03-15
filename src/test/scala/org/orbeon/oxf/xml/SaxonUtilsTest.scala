/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xml

import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._
import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test

class SaxonUtilsTest extends AssertionsForJUnit {

  @Test def makeNCName(): Unit = {

    intercept[IllegalArgumentException] {
      SaxonUtils.makeNCName("", keepFirstIfPossible = true)
    }

    intercept[IllegalArgumentException] {
      SaxonUtils.makeNCName("  ", keepFirstIfPossible = true)
    }

    assert("foo"      === SaxonUtils.makeNCName("foo",      keepFirstIfPossible = true))
    assert("_foo_"    === SaxonUtils.makeNCName(" foo ",    keepFirstIfPossible = true))
    assert("_42foos"  === SaxonUtils.makeNCName("42foos",   keepFirstIfPossible = true))
    assert("_2foos"   === SaxonUtils.makeNCName("42foos",   keepFirstIfPossible = false))
    assert("foo_bar_" === SaxonUtils.makeNCName("foo(bar)", keepFirstIfPossible = true))
  }

  @Test def testBuildNodePathHandleNamespaces(): Unit = {

    val doc: NodeInfo =
      <xh:html
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xh="http://www.w3.org/1999/xhtml">
        <xh:head>
            <xf:model>
                <xf:instance>
                    <fr:databound-select1 id="control-3-control" appearance="minimal" resource="" bind="control-3-bind">
                        <xf:label ref="$form-resources/control-3/label"/>
                        <xf:hint ref="$form-resources/control-3/hint"/>
                        <xf:alert ref="$fr-resources/detail/labels/alert"/>
                        <xf:itemset ref="item">
                            <xf:label ref="label"/>
                            <xf:value ref="value"/>
                        </xf:itemset>
                    </fr:databound-select1>
                </xf:instance>
            </xf:model>
        </xh:head>
        <xh:body>
            <xf:input ref="@resource">
                <xf:label lang="en">Resource URL</xf:label>
                <xf:hint lang="en">HTTP URL returning data used to populate the dropdown</xf:hint>
            </xf:input>
            <xf:input ref="xf:itemset/@ref">
                <xf:label>Items</xf:label>
                <xf:hint>XPath expression returning one node for each item</xf:hint>
            </xf:input>
            <xf:input ref="xf:itemset/xf:label/@ref">
                <xf:label>Label</xf:label>
                <xf:hint>XPath expression relative to an item node</xf:hint>
            </xf:input>
            <xf:input ref="xf:itemset/xf:value/@ref">
                <xf:label>Value</xf:label>
                <xf:hint>XPath expression relative to an item node</xf:hint>
            </xf:input>
        </xh:body>
    </xh:html>

    val expected = List[(NodeInfo, List[String])](
      doc.root →
        Nil,
      doc.rootElement →
        List(
          "*:html[namespace-uri() = 'http://www.w3.org/1999/xhtml']"
        ),
      (doc descendant "*:label" head) →
        List(
          "*:html[namespace-uri() = 'http://www.w3.org/1999/xhtml']",
          "*:head[namespace-uri() = 'http://www.w3.org/1999/xhtml'][1]",
          "*:model[namespace-uri() = 'http://www.w3.org/2002/xforms'][1]",
          "*:instance[namespace-uri() = 'http://www.w3.org/2002/xforms'][1]",
          "*:databound-select1[namespace-uri() = 'http://orbeon.org/oxf/xml/form-runner'][1]",
          "*:label[namespace-uri() = 'http://www.w3.org/2002/xforms'][1]"
        ),
      (doc descendant * att "appearance" head) →
        List(
          "*:html[namespace-uri() = 'http://www.w3.org/1999/xhtml']",
          "*:head[namespace-uri() = 'http://www.w3.org/1999/xhtml'][1]",
          "*:model[namespace-uri() = 'http://www.w3.org/2002/xforms'][1]",
          "*:instance[namespace-uri() = 'http://www.w3.org/2002/xforms'][1]",
          "*:databound-select1[namespace-uri() = 'http://orbeon.org/oxf/xml/form-runner'][1]",
          "@appearance"
        ),
      (doc descendant "*:input" apply 3) →
        List(
          "*:html[namespace-uri() = 'http://www.w3.org/1999/xhtml']",
          "*:body[namespace-uri() = 'http://www.w3.org/1999/xhtml'][1]",
          "*:input[namespace-uri() = 'http://www.w3.org/2002/xforms'][4]"
        )
    )

    for ((node, path) ← expected)
      assert(path === SaxonUtils.buildNodePath(node))
  }
}