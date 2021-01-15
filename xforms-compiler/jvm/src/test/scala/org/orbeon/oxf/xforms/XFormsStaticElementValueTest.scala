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
        Left("""concat(' ', string(($label-or-placeholder)[1]), ' <span class="',  xxf:evaluate-avt('fr-error-alert fr-error-alert-{@level}') , '"> ', string((@alert)[1]), ' </span> ')"""),
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
        Left("""concat(' <span> ', string((my/path)[1]), ' <i class="',  xxf:evaluate-avt('{''my-class''[$var = 42]}') , '"></i> </span> <span class="static-class" id="prefix$my-id"><b>Bold text for ', string((bind('my-bind'))[1]), '!</b></span> ')"""),
        true
      ),
      (
        "static with HTML",
        <xf:label xmlns:xf="http://www.w3.org/2002/xforms"><i>Italics</i></xf:label>.toDocument,
        Right("<i>Italics</i>"),
        true
      ),
      (
        "static without HTML",
        <xf:label xmlns:xf="http://www.w3.org/2002/xforms">First name:</xf:label>.toDocument,
        Right("First name:"),
        false
      ),
    )

    for ((desc, doc, expectedExprOrConst, expectedContainsHTML) <- Expected)
      it(s"must pass for $desc") {

        val (expressionOrConstant, containsHTML) =
          XFormsStaticElementValue.getElementExpressionOrConstant(
            outerElem       = doc.getRootElement,
            containerPrefix = "prefix$",
            acceptHTML      = true,
          )

        assert(expectedExprOrConst  == expressionOrConstant)
        assert(expectedContainsHTML == containsHTML)
      }
  }
}
