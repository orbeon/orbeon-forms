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
package org.orbeon.oxf.fr

import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.test.ResourceManagerSupport
import org.orbeon.oxf.xml.{AllowedFunctions, XMLConstants}
import org.scalatest.funspec.AnyFunSpecLike


class FormRunnerAllowedFunctionsTest extends AnyFunSpecLike with ResourceManagerSupport {

  val FnUri   = "http://www.w3.org/2005/xpath-functions"
  val MathUri = "http://www.w3.org/2005/xpath-functions/math"
  val XxfUri  = "http://orbeon.org/oxf/xml/xforms"
  val FrUri   = "http://orbeon.org/oxf/xml/form-runner"

  describe("FormRunnerAllowedFunctions") {

    it("should resolve allowed functions for a form from the private baseline property") {
      val af = FormRunnerAllowedFunctions.allowedFunctions("orbeon", "any-form")
      assert(af.isAllowed(FnUri, "concat"))
      assert(af.isAllowed("http://www.w3.org/2002/xforms", "parse"))
      assert(!af.isAllowed(FnUri, "doc"))
      assert(!af.isAllowed(XxfUri, "call-xpl"))
      assert(!af.isAllowed(XxfUri, "doc"))
      assert(!af.isAllowed("java:java.lang.System", "currentTimeMillis"))
    }

    it("should convert allowed functions property to UriQualifiedNames") {
      val convertedOpt = FormRunnerAllowedFunctions.allowedFunctionsPropertyAsUriQualifiedNames("orbeon", "any-form", None)
      assert(convertedOpt.isDefined)
      val converted = convertedOpt.get
      assert(converted.contains("Q{http://www.w3.org/2005/xpath-functions/math}*"))
      assert(converted.contains("Q{http://www.w3.org/2005/xpath-functions}*"))
      assert(converted.contains("-Q{http://www.w3.org/2005/xpath-functions}doc"))
      assert(converted.contains("-Q{http://orbeon.org/oxf/xml/xforms}call-xpl"))
    }

    it("should apply user-defined oxf.fr.detail.formula.allowed-functions updates on top of the private baseline") {
      val customNsUri = "http://example.com/custom-functions"
      val baseNs = Map("fn" -> FnUri, "math" -> MathUri, "xxf" -> XxfUri)
      val propertySet = PropertySet.forTests(List(
        PropertyParams(
          namespaces  = baseNs,
          name        = "oxf.fr.detail.formula.allowed-functions.private",
          typeQName   = XMLConstants.XS_STRING_QNAME,
          stringValue = "math:* fn:* -fn:doc -xxf:call-xpl"
        ),
        PropertyParams(
          namespaces  = baseNs + ("custom" -> customNsUri),
          name        = "oxf.fr.detail.formula.allowed-functions.my-app.my-form",
          typeQName   = XMLConstants.XS_STRING_QNAME,
          stringValue = "+custom:my-func +fn:doc -math:*"
        )
      ))

      // 1. Direct resolution via allowedFunctions
      val af = FormRunnerAllowedFunctions.allowedFunctions("my-app", "my-form", None, propertySet)
      assert(af.isAllowed(FnUri, "concat"))
      assert(af.isAllowed(FnUri, "doc")) // re-allowed by user property
      assert(af.isAllowed(customNsUri, "my-func")) // added by user property with custom namespace
      assert(!af.isAllowed(MathUri, "sqrt")) // denied by user property
      assert(!af.isAllowed(XxfUri, "call-xpl")) // still denied from baseline

      // Other forms not matching my-app/my-form only get the baseline
      val afOther = FormRunnerAllowedFunctions.allowedFunctions("other-app", "other-form", None, propertySet)
      assert(afOther.isAllowed(FnUri, "concat"))
      assert(!afOther.isAllowed(FnUri, "doc"))
      assert(!afOther.isAllowed(customNsUri, "my-func"))
      assert(afOther.isAllowed(MathUri, "sqrt"))

      // 2. Resolution via UriQualifiedName conversion (used by components.xsl -> xxf:allowed-functions)
      val convertedOpt = FormRunnerAllowedFunctions.allowedFunctionsPropertyAsUriQualifiedNames("my-app", "my-form", None, propertySet)
      assert(convertedOpt.isDefined)
      val afFromConverted = AllowedFunctions.parse(convertedOpt.get)
      assert(afFromConverted.isAllowed(FnUri, "concat"))
      assert(afFromConverted.isAllowed(FnUri, "doc"))
      assert(afFromConverted.isAllowed(customNsUri, "my-func"))
      assert(!afFromConverted.isAllowed(MathUri, "sqrt"))
      assert(!afFromConverted.isAllowed(XxfUri, "call-xpl"))
    }

    it("should allow user property to reset baseline with -*") {
      val baseNs = Map("fn" -> FnUri, "math" -> MathUri, "xxf" -> XxfUri)
      val propertySet = PropertySet.forTests(List(
        PropertyParams(
          namespaces  = baseNs,
          name        = "oxf.fr.detail.formula.allowed-functions.private",
          typeQName   = XMLConstants.XS_STRING_QNAME,
          stringValue = "math:* fn:* -fn:doc -xxf:call-xpl"
        ),
        PropertyParams(
          namespaces  = baseNs,
          name        = "oxf.fr.detail.formula.allowed-functions.app-all.form-all",
          typeQName   = XMLConstants.XS_STRING_QNAME,
          stringValue = "-* *"
        ),
        PropertyParams(
          namespaces  = baseNs,
          name        = "oxf.fr.detail.formula.allowed-functions.app-reset.form-reset",
          typeQName   = XMLConstants.XS_STRING_QNAME,
          stringValue = "-* fn:concat"
        )
      ))

      val afAll = FormRunnerAllowedFunctions.allowedFunctions("app-all", "form-all", None, propertySet)
      assert(afAll.isAllowed(FnUri, "doc"))
      assert(afAll.isAllowed(XxfUri, "call-xpl"))
      assert(afAll.isAllowed("java:java.lang.System", "currentTimeMillis"))

      val afReset = FormRunnerAllowedFunctions.allowedFunctions("app-reset", "form-reset", None, propertySet)
      assert(afReset.isAllowed(FnUri, "concat"))
      assert(!afReset.isAllowed(FnUri, "string-length"))
      assert(!afReset.isAllowed(MathUri, "sqrt"))
    }
  }
}
