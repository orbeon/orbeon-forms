package org.orbeon.oxf.xforms

import org.orbeon.oxf.xml.dom.Converter._
import org.scalatest.funspec.AnyFunSpec


class XFormsStaticElementValueTest extends AnyFunSpec {

  describe("Extract expression or constant from LHHA element") {

    val Expected = List(
      (
        "error summary example",
        <xf:label xmlns:xf="http://www.w3.org/2002/xforms" xmlns:xh="http://www.w3.org/1999/xhtml">
            <xf:output
                mediatype="text/html"
                value="$label-or-placeholder"/>
            <span class="fr-error-alert fr-error-alert-{@level}">
                <xf:output
                    mediatype="text/html"
                    value="@alert"/>
            </span>
        </xf:label>.toDocument,
        false,
        Left("""concat(' ', string(($label-or-placeholder)[1]), ' <span class="', xxf:evaluate-avt('fr-error-alert fr-error-alert-{@level}'), '"> ', string((@alert)[1]), ' </span> ')"""),
        true
      ),
      (
        "mixed example",
        <xf:label xmlns:xf="http://www.w3.org/2002/xforms" xmlns:xh="http://www.w3.org/1999/xhtml">
            <xh:span>
                <xf:output ref="my/path"/>
                <xh:i class="{'my-class'[$var = 42]}"></xh:i>
            </xh:span>
            <xh:span class="static-class" id="my-id"><xh:b>Bold text for <xf:output bind="my-bind"/>!</xh:b></xh:span>
        </xf:label>.toDocument,
        false,
        Left("""concat(' <span> ', string((my/path)[1]), ' <i class="', xxf:evaluate-avt('{''my-class''[$var = 42]}'), '"></i> </span> <span class="static-class" id="prefix$my-id"><b>Bold text for ', string((bind('my-bind'))[1]), '!</b></span> ')"""),
        true
      ),
      (
        "static with HTML",
        <xf:label xmlns:xf="http://www.w3.org/2002/xforms"><i>Italics</i></xf:label>.toDocument,
        false,
        Right("<i>Italics</i>"),
        true
      ),
      (
        "static without HTML",
        <xf:label xmlns:xf="http://www.w3.org/2002/xforms">First name:</xf:label>.toDocument,
        false,
        Right("First name:"),
        false
      ),
      (
        "mixed example within repeat with ids",
        <xf:label xmlns:xf="http://www.w3.org/2002/xforms" xmlns:xh="http://www.w3.org/1999/xhtml">
            <xh:span>
                <xf:output id="my-output" ref="my/path"/>
                <xh:i id="my-i" class="{'my-class'[$var = 42]}"></xh:i>
            </xh:span>
            <xh:span class="static-class" id="my-id"><xh:b>Bold text for <xf:output bind="my-bind"/>!</xh:b></xh:span>
        </xf:label>.toDocument,
        true,
        Left("""concat(' <span> ', string((my/path)[1]), ' <i id="prefix$my-i⊙', string-join(for $p in xxf:repeat-positions() return string($p), '-'), '" class="', xxf:evaluate-avt('{''my-class''[$var = 42]}'), '"></i> </span> <span class="static-class" id="prefix$my-id⊙', string-join(for $p in xxf:repeat-positions() return string($p), '-'), '"><b>Bold text for ', string((bind('my-bind'))[1]), '!</b></span> ')"""),
        true
      ),
    )

    for ((desc, doc, isWithinRepeat, expectedExprOrConst, expectedContainsHTML) <- Expected)
      it(s"must pass for $desc") {

        val (expressionOrConstant, containsHTML) =
          XFormsStaticElementValue.getElementExpressionOrConstant(
            outerElem       = doc.getRootElement,
            containerPrefix = "prefix$",
            isWithinRepeat  = isWithinRepeat,
            acceptHTML      = true,
          )

        assert(expectedExprOrConst  == expressionOrConstant)
        assert(expectedContainsHTML == containsHTML)
      }
  }
}
