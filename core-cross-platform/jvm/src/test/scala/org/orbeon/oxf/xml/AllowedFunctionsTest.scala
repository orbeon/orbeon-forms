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
package org.orbeon.oxf.xml

import org.orbeon.oxf.util.StaticXPath
import org.orbeon.saxon.trans.XPathException
import org.orbeon.xml.NamespaceMapping
import org.scalatest.funspec.AnyFunSpecLike


class AllowedFunctionsTest extends AnyFunSpecLike {

  val FnUri   = "http://www.w3.org/2005/xpath-functions"
  val MathUri = "http://www.w3.org/2005/xpath-functions/math"
  val XsUri   = "http://www.w3.org/2001/XMLSchema"
  val XxfUri  = "http://orbeon.org/oxf/xml/xforms"
  val FrUri   = "http://orbeon.org/oxf/xml/form-runner"
  val FrfUri  = "java:org.orbeon.oxf.fr.FormRunner"
  val ExfUri1 = "http://www.exforms.org/exf/1-0"

  val StandardNs: NamespaceMapping = NamespaceMapping(Map(
    "fn"   -> FnUri,
    "math" -> MathUri,
    "xs"   -> XsUri,
    "xxf"  -> XxfUri,
    "fr"   -> FrUri,
    "exf"  -> ExfUri1
  ))

  describe("AllowedFunctions parser and rules") {

    it("should deny everything on Empty") {
      val af = AllowedFunctions.Empty
      assert(!af.isAllowed(FnUri, "concat"))
      assert(!af.isAllowed(MathUri, "sqrt"))
      assert(!af.isAllowed("java:java.lang.Math", "abs"))
    }

    it("should allow everything on * or *:*") {
      val af = AllowedFunctions.parse("*")
      assert(af.isAllowed(FnUri, "concat"))
      assert(af.isAllowed(MathUri, "sqrt"))
      assert(af.isAllowed("http://example.com/custom", "foo"))
      assert(af.isAllowed("java:java.lang.System", "currentTimeMillis"))
    }

    it("should support wildcard namespace tokens with in-scope NamespaceMapping") {
      val af = AllowedFunctions.parse("fn:* math:* xs:*", StandardNs)
      assert(af.isAllowed(FnUri, "concat"))
      assert(af.isAllowed(FnUri, "string-length"))
      assert(af.isAllowed(MathUri, "sqrt"))
      assert(af.isAllowed(XsUri, "date"))
      assert(!af.isAllowed(XxfUri, "instance"))
      assert(!af.isAllowed("java:java.lang.Math", "abs"))
    }

    it("should ignore prefixed QName tokens when no NamespaceMapping is provided") {
      val af = AllowedFunctions.parse("fn:* math:sqrt")
      assert(!af.isAllowed(FnUri, "concat"))
      assert(!af.isAllowed(MathUri, "sqrt"))
    }

    it("should support specific function QNames and denials with in-scope NamespaceMapping") {
      val af = AllowedFunctions.parse("fn:* -fn:doc -fn:collection +xxf:instance", StandardNs)
      assert(af.isAllowed(FnUri, "concat"))
      assert(!af.isAllowed(FnUri, "doc"))
      assert(!af.isAllowed(FnUri, "collection"))
      assert(af.isAllowed(XxfUri, "instance"))
      assert(!af.isAllowed(XxfUri, "evaluate"))
    }

    it("should support unprefixed function names") {
      val af = AllowedFunctions.parse("concat -doc")
      assert(af.isAllowed(FnUri, "concat"))
      assert(!af.isAllowed(FnUri, "doc"))
      assert(!af.isAllowed(FnUri, "string-length"))
    }

    it("should support custom prefix mappings") {
      val ns = NamespaceMapping(Map("custom" -> "http://example.com/custom"))
      val af = AllowedFunctions.parse("custom:* -custom:dangerous", ns)
      assert(af.isAllowed("http://example.com/custom", "safe"))
      assert(!af.isAllowed("http://example.com/custom", "dangerous"))
    }

    it("should support UriQualifiedName syntax in AllowedFunctions.parse") {
      val af = AllowedFunctions.parse("Q{http://example.com/custom}* -Q{http://example.com/custom}secret +Q{http://example.com/other}func")
      assert(af.isAllowed("http://example.com/custom", "any"))
      assert(!af.isAllowed("http://example.com/custom", "secret"))
      assert(af.isAllowed("http://example.com/other", "func"))
      assert(!af.isAllowed("http://example.com/other", "other-func"))

      // Empty namespace Q{} refers to default function namespace
      val afEmpty = AllowedFunctions.parse("Q{}concat -Q{}doc")
      assert(afEmpty.isAllowed(FnUri, "concat"))
      assert(!afEmpty.isAllowed(FnUri, "doc"))

      // eXForms URI
      val afExf = AllowedFunctions.parse("Q{http://www.exforms.org/exf/1-0}*")
      assert(afExf.isAllowed(ExfUri1, "sort"))

      // Java function via UriQualifiedName
      val afJava = AllowedFunctions.parse(s"Q{$FnUri}* +Q{java:java.lang.Math}abs")
      assert(afJava.isAllowed(FnUri, "concat"))
      assert(afJava.isAllowed("java:java.lang.Math", "abs"))
      assert(!afJava.isAllowed("java:java.lang.Math", "sin"))
    }

    it("should convert prefixed tokens to UriQualifiedNames with convertToUriQualifiedNames") {
      val ns = NamespaceMapping(Map(
        "fn"     -> FnUri,
        "math"   -> MathUri,
        "my"     -> "http://example.com/my",
        "custom" -> "http://example.com/custom"
      ))

      val inputSpec =
        """math:*
          |my:my-func
          |-custom:secret
          |+custom:allowed
          |my:*
          |-custom:*
          |Q{http://example.com/direct}direct-func
          |-Q{http://example.com/direct}denied-direct
          |*
          |java:*
          |java:java.lang.Math:abs
          |-fn:doc
          |concat
          |""".stripMargin

      val converted = AllowedFunctions.convertToUriQualifiedNames(inputSpec, ns)

      assert(converted.contains("Q{http://www.w3.org/2005/xpath-functions/math}*"))
      assert(converted.contains("Q{http://example.com/my}my-func"))
      assert(converted.contains("-Q{http://example.com/custom}secret"))
      assert(converted.contains("+Q{http://example.com/custom}allowed"))
      assert(converted.contains("Q{http://example.com/my}*"))
      assert(converted.contains("-Q{http://example.com/custom}*"))
      assert(converted.contains("Q{http://example.com/direct}direct-func"))
      assert(converted.contains("-Q{http://example.com/direct}denied-direct"))
      assert(converted.contains("*"))
      assert(converted.contains("java:*"))
      assert(converted.contains("java:java.lang.Math:abs"))
      assert(converted.contains("-Q{http://www.w3.org/2005/xpath-functions}doc"))
      assert(converted.contains("concat"))

      // Parsing the converted specification directly (without custom prefix mappings) must produce the expected rules
      val afParsed = AllowedFunctions.parse(converted)
      assert(afParsed.isAllowed(MathUri, "pow"))
      assert(afParsed.isAllowed("http://example.com/my", "my-func"))
      assert(!afParsed.isAllowed("http://example.com/custom", "secret"))
      assert(afParsed.isAllowed("http://example.com/custom", "allowed"))
      assert(afParsed.isAllowed("http://example.com/my", "other"))
      assert(!afParsed.isAllowed("http://example.com/custom", "other"))
      assert(afParsed.isAllowed("http://example.com/direct", "direct-func"))
      assert(!afParsed.isAllowed("http://example.com/direct", "denied-direct"))
      assert(afParsed.isAllowed("java:java.lang.Math", "abs"))
      assert(!afParsed.isAllowed(FnUri, "doc"))
      assert(afParsed.isAllowed(FnUri, "concat"))
    }

    it("should correctly handle Java functions and restrictions") {
      // Deny Java globally, but allow abs
      val af1 = AllowedFunctions.parse("* -java:* +java:java.lang.Math:abs")
      assert(af1.isAllowed(FnUri, "concat"))
      assert(af1.isAllowed("java:java.lang.Math", "abs"))
      assert(!af1.isAllowed("java:java.lang.Math", "sin"))
      assert(!af1.isAllowed("java:java.lang.System", "currentTimeMillis"))

      // Allow a specific Java class
      val af2 = AllowedFunctions.parse("-java:* +java:java.lang.Math:*")
      assert(af2.isAllowed("java:java.lang.Math", "abs"))
      assert(af2.isAllowed("java:java.lang.Math", "sin"))
      assert(!af2.isAllowed("java:java.lang.System", "currentTimeMillis"))

      // Deny Java with -java:* when allowAll is false
      val af3 = AllowedFunctions.parse(s"Q{$FnUri}* -java:*")
      assert(af3.isAllowed(FnUri, "concat"))
      assert(!af3.isAllowed("java:java.lang.Math", "abs"))
    }

    it("should respect precedence: explicit QName > namespace denial") {
      val af = AllowedFunctions.parse("fn:* -fn:* +fn:concat", StandardNs)
      // -fn:* denies the namespace, but +fn:concat explicitly allows concat
      assert(af.isAllowed(FnUri, "concat"))
      assert(!af.isAllowed(FnUri, "string-length"))
    }

    it("should reset with -*") {
      val af1 = AllowedFunctions.parse("fn:* math:*", StandardNs)
      val af2 = af1.update("-* xs:*", StandardNs)
      assert(!af2.isAllowed(FnUri, "concat"))
      assert(!af2.isAllowed(MathUri, "sqrt"))
      assert(af2.isAllowed(XsUri, "date"))
    }

    it("should conform to Default configuration") {
      val af = AllowedFunctions.Default
      // Allowed namespaces
      assert(af.isAllowed(FnUri, "concat"))
      assert(af.isAllowed(FnUri, "string-length"))
      assert(af.isAllowed("http://www.w3.org/2002/xforms", "parse"))
      assert(af.isAllowed("http://www.w3.org/2002/xforms", "element"))
      assert(af.isAllowed(MathUri, "sqrt"))
      assert(af.isAllowed(XsUri, "date"))
      assert(af.isAllowed("http://www.w3.org/2005/xpath-functions/map", "get"))
      assert(af.isAllowed("http://www.w3.org/2005/xpath-functions/array", "size"))
      assert(af.isAllowed(XxfUri, "split"))
      assert(af.isAllowed(ExfUri1, "sort"))
      assert(af.isAllowed(FrUri, "control-string-value"))

      // Denied functions in Default
      assert(!af.isAllowed(FnUri, "doc"))
      assert(!af.isAllowed(FnUri, "doc-available"))
      assert(!af.isAllowed(FnUri, "collection"))
      assert(!af.isAllowed(FnUri, "unparsed-text"))
      assert(!af.isAllowed(FnUri, "unparsed-text-available"))
      assert(!af.isAllowed(FnUri, "unparsed-text-lines"))
      assert(!af.isAllowed(FnUri, "uri-collection"))
      assert(!af.isAllowed(FnUri, "environment-variable"))
      assert(!af.isAllowed(FnUri, "available-environment-variables"))
      assert(!af.isAllowed(FnUri, "load-xquery-module"))
      assert(!af.isAllowed(FnUri, "transform"))
      assert(!af.isAllowed(XxfUri, "evaluate"))
      assert(!af.isAllowed(XxfUri, "evaluate-in-context"))
      assert(!af.isAllowed(XxfUri, "call-xpl"))
      assert(!af.isAllowed(XxfUri, "instance"))
      assert(!af.isAllowed(XxfUri, "doc"))
      assert(!af.isAllowed(XxfUri, "doc-base64"))
      assert(!af.isAllowed(XxfUri, "doc-base64-available"))
      assert(!af.isAllowed(XxfUri, "image-metadata"))
      assert(!af.isAllowed(XxfUri, "property"))
      assert(!af.isAllowed(XxfUri, "properties-start-with"))
      assert(!af.isAllowed(XxfUri, "get-session-attribute"))
      assert(!af.isAllowed(XxfUri, "set-session-attribute"))
      assert(!af.isAllowed(XxfUri, "get-request-attribute"))
      assert(!af.isAllowed(XxfUri, "set-request-attribute"))
      assert(!af.isAllowed(XxfUri, "get-document-attribute"))
      assert(!af.isAllowed(XxfUri, "set-document-attribute"))
      assert(!af.isAllowed(XxfUri, "remove-document-attribute"))
      assert(!af.isAllowed(XxfUri, "remove-document-attributes"))
      assert(!af.isAllowed(FrUri, "evaluate-from-property"))
      assert(!af.isAllowed(FrUri, "run-process"))
      assert(!af.isAllowed(FrUri, "save-state"))
      assert(!af.isAllowed(FrUri, "transform-uploaded-image"))

      // Java is not allowed
      assert(!af.isAllowed("java:java.lang.System", "currentTimeMillis"))
      assert(!af.isAllowed("java:java.lang.Math", "abs"))

      // Saxon is not allowed
      assert(!af.isAllowed("http://saxon.sf.net/", "evaluate"))
    }

    it("should allow compiler helpers via withCompilerHelpers") {
      // Even with Empty or denied Java / fn
      val af = AllowedFunctions.parse("-java:* -fn:*", StandardNs).withCompilerHelpers
      assert(af.isAllowed(FrfUri, "controlVariableValue"))
      assert(af.isAllowed(FrfUri, "controlVariableValueForAction"))
      assert(af.isAllowed("http://orbeon.org/oxf/xml/form-builder", "lang"))
      assert(af.isAllowed(XxfUri, "evaluate-avt"))
      assert(af.isAllowed(XxfUri, "r"))
      assert(af.isAllowed(XxfUri, "escape-xml-minimal"))

      // Non-injected action/XBL helpers are no longer in CompilerHelperFunctions
      assert(!af.isAllowed(FrfUri, "bindId"))
      assert(!af.isAllowed(FrfUri, "repeatClear"))
      assert(!af.isAllowed("java:org.orbeon.oxf.util.SecureUtils", "randomHexId"))
      assert(!af.isAllowed(XxfUri, "get-document-attribute"))
      assert(!af.isAllowed(XxfUri, "instance"))

      // Other Java or fn functions still denied
      assert(!af.isAllowed("java:java.lang.System", "currentTimeMillis"))
      assert(!af.isAllowed(FnUri, "concat"))
    }
  }

  describe("WhitelistedFunctionLibrary with XPath compilation") {

    val ns = NamespaceMapping(Map(
      "fn"   -> FnUri,
      "math" -> MathUri,
      "xs"   -> XsUri,
      "xxf"  -> XxfUri,
      "sys"  -> "java:java.lang.System",
      "math-java" -> "java:java.lang.Math"
    ))

    def compile(xpath: String, af: AllowedFunctions): StaticXPath.CompiledExpression = {
      val wfl = WhitelistedFunctionLibrary(null, af)
      StaticXPath.compileExpression(
        xpathString      = xpath,
        namespaceMapping = ns,
        locationData     = null,
        functionLibrary  = wfl,
        avt              = false
      )(null)
    }

    it("should compile and evaluate allowed standard functions") {
      val af = AllowedFunctions.Default
      val expr = compile("string-length('hello')", af)
      assert(expr ne null)
    }

    it("should throw XPathException when compiling a denied function (e.g. fn:doc)") {
      val af = AllowedFunctions.Default
      val ex = intercept[XPathException] {
        compile("fn:doc('secret.xml')", af)
      }
      assert(ex.getMessage.contains("doc"))
      assert(ex.getMessage.contains("not allowed"))
    }

    it("should throw XPathException when compiling disallowed Java call") {
      val af = AllowedFunctions.Default
      val ex = intercept[XPathException] {
        compile("sys:currentTimeMillis()", af)
      }
      assert(ex.getMessage.contains("currentTimeMillis"))
      assert(ex.getMessage.contains("not allowed"))
    }

    it("should allow Java function when explicitly whitelisted") {
      val af = AllowedFunctions.parse(s"Q{$FnUri}* +java:java.lang.Math:abs")
      val expr = compile("math-java:abs(-42)", af)
      assert(expr ne null)
      val dc = expr.expression.createDynamicContext(null)
      assert(expr.expression.evaluateSingle(dc).getStringValue == "42")
    }

    it("should report isAvailable() correctly respecting allowed functions") {
      import org.orbeon.saxon.om.StructuredQName
      import org.orbeon.saxon.functions.FunctionLibrary
      import org.orbeon.saxon.expr.{Expression, StaticContext}

      val mockLibrary = new FunctionLibrary {
        def isAvailable(symbolicName: StructuredQName, arity: Int): Boolean = true
        def bind(symbolicName: StructuredQName, staticArgs: Array[Expression], env: StaticContext): Expression = null
        def copy(): FunctionLibrary = this
      }

      val af = AllowedFunctions.Default
      val wfl = WhitelistedFunctionLibrary(mockLibrary, af)

      assert(wfl.isAvailable(new StructuredQName("", FnUri, "string-length"), 1))
      assert(!wfl.isAvailable(new StructuredQName("", FnUri, "doc"), 1))
      assert(!wfl.isAvailable(new StructuredQName("", "java:java.lang.System", "currentTimeMillis"), 0))

      val afWithJava = AllowedFunctions.parse(s"Q{$FnUri}* +java:java.lang.Math:abs")
      val wflJava = WhitelistedFunctionLibrary(mockLibrary, afWithJava)
      assert(wflJava.isAvailable(new StructuredQName("", "java:java.lang.Math", "abs"), 1))
      assert(!wflJava.isAvailable(new StructuredQName("", "java:java.lang.Math", "sin"), 1))
    }
  }
}
