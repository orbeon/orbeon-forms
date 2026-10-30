package org.orbeon.oxf.fb

import org.orbeon.dom.Document
import org.orbeon.oxf.fr.FormRunnerSupport
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.{IndentedLogger, StaticXPath}
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.orbeon.oxf.xforms.control.controls.XXFormsDynamicControl
import org.orbeon.saxon.trans.XPathException
import org.orbeon.scaxon.NodeConversions
import org.orbeon.xforms.xbl.Scope
import org.scalatest.funspec.AnyFunSpecLike

class FormBuilderRestrictedFunctionsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  describe("Form Builder Restricted Functions (#7266)") {

    it("must allow Form Builder itself to load successfully") {
      val (processorService, docOpt, _) =
        runFormRunner("orbeon", "builder", "new")

      assert(docOpt.isDefined)
      val doc = docOpt.get

      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, doc) {
          val fbDynamicControl = resolveObject[XXFormsDynamicControl]("fb")
          assert(fbDynamicControl.isDefined)
        }
      }
    }

    it("must allow whitelisted functions in the dynamic form under xxf:dynamic") {
      val (processorService, docOpt, _) =
        runFormRunner("orbeon", "builder", "new")

      assert(docOpt.isDefined)
      val doc = docOpt.get

      implicit val logger: IndentedLogger = doc.staticState.getIndentedLogger

      val parentPart = doc.staticState.topLevelPart
      val dynamicScope = new Scope(Some(parentPart.startScope), "fb")

      val formWithAllowedXml: Document = NodeConversions.elemToOrbeonDom(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:math="http://www.w3.org/2005/xpath-functions/math"
          xmlns:saxon="http://saxon.sf.net/">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <control-1/>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')" xxf:functions="restricted">
                <xf:bind id="c1-bind" ref="control-1" calculate="concat('hello', 'world')"/>
              </xf:bind>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:group id="fb-body" class="fb-body fr-view-appearance-full" xxf:functions="restricted">
              <xf:output id="allowed-control" value="math:pow(2, 3)"/>
            </xf:group>
          </xh:body>
        </xh:html>
      )

      val (_, newPart) = PartAnalysisBuilder.createPart(
        doc.staticState,
        parentPart,
        formWithAllowedXml,
        dynamicScope
      )

      assert(newPart ne null)
      assert(newPart.getStaticXPathErrors.isEmpty)

      val allowedControl = newPart.findControlAnalysis("fb≡allowed-control").get
      assert(allowedControl.isFunctionsRestricted)
    }

    it("must not allow bypassing restrictions by injecting class='fb-annotation' or class='fr-design-time-preserve'") {
      val (_, docOpt, _) =
        runFormRunner("orbeon", "builder", "new")

      assert(docOpt.isDefined)
      val doc = docOpt.get

      implicit val logger: IndentedLogger = doc.staticState.getIndentedLogger

      val parentPart = doc.staticState.topLevelPart
      val dynamicScope = new Scope(Some(parentPart.startScope), "fb")

      val formWithInjectedClassXml: Document = NodeConversions.elemToOrbeonDom(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form><control-1/></form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')" xxf:functions="restricted">
                <xf:bind id="c1-bind" ref="control-1" calculate="doc('secret.xml')" class="fr-design-time-preserve"/>
              </xf:bind>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:group id="fb-body" class="fb-body fr-view-appearance-full" xxf:functions="restricted">
              <xf:output id="injected-control" value="doc('secret.xml')" class="fb-annotation"/>
            </xf:group>
          </xh:body>
        </xh:html>
      )

      val (_, newPart) = PartAnalysisBuilder.createPart(
        doc.staticState,
        parentPart,
        formWithInjectedClassXml,
        dynamicScope
      )

      // Both bind and control must still be restricted despite the injected classes
      val injectedControl = newPart.findControlAnalysis("fb≡injected-control").get
      assert(injectedControl.isFunctionsRestricted)

      val errors = newPart.getStaticXPathErrors
      assert(errors.exists { case (_, throwableOpt, _) =>
        throwableOpt.exists { t =>
          val rootCause = Iterator.iterate[Throwable](t)(_.getCause).takeWhile(_ ne null).toList
          rootCause.exists(rc => rc.isInstanceOf[XPathException] && rc.getMessage.contains("is not allowed in user-defined form expressions"))
        }
      })
    }

    it("must reject disallowed functions in dynamic form controls under xxf:dynamic") {
      val (_, docOpt, _) =
        runFormRunner("orbeon", "builder", "new")

      assert(docOpt.isDefined)
      val doc = docOpt.get

      implicit val logger: IndentedLogger = doc.staticState.getIndentedLogger

      val parentPart = doc.staticState.topLevelPart
      val dynamicScope = new Scope(Some(parentPart.startScope), "fb")

      val formWithDisallowedDocXml: Document = NodeConversions.elemToOrbeonDom(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form><control-1/></form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')" xxf:functions="restricted"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:group id="fb-body" class="fb-body fr-view-appearance-full" xxf:functions="restricted">
              <xf:output id="disallowed-control" value="doc('secret.xml')"/>
            </xf:group>
          </xh:body>
        </xh:html>
      )

      val (_, newPart) = PartAnalysisBuilder.createPart(
        doc.staticState,
        parentPart,
        formWithDisallowedDocXml,
        dynamicScope
      )

      val disallowedControl = newPart.findControlAnalysis("fb≡disallowed-control").get
      assert(disallowedControl.isFunctionsRestricted)

      val ex = intercept[XPathException] {
        StaticXPath.compileExpression(
          xpathString      = disallowedControl.value.get,
          namespaceMapping = newPart.getNamespaceMapping("fb≡", disallowedControl.element),
          locationData     = null,
          functionLibrary  = newPart.functionLibrary(disallowedControl.isFunctionsRestricted),
          avt              = false
        )
      }
      assert(ex.getMessage.contains("is not allowed in user-defined form expressions"))
    }

    it("must reject disallowed functions in dynamic form binds under xxf:dynamic") {
      val (_, docOpt, _) =
        runFormRunner("orbeon", "builder", "new")

      assert(docOpt.isDefined)
      val doc = docOpt.get

      implicit val logger: IndentedLogger = doc.staticState.getIndentedLogger

      val parentPart = doc.staticState.topLevelPart
      val dynamicScope = new Scope(Some(parentPart.startScope), "fb")

      val formWithDisallowedBindXml: Document = NodeConversions.elemToOrbeonDom(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form><control-1/></form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')" xxf:functions="restricted">
                <xf:bind id="c1-bind" ref="control-1" calculate="doc('secret.xml')"/>
              </xf:bind>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:group id="fb-body" class="fb-body fr-view-appearance-full" xxf:functions="restricted">
              <xf:output id="c1" ref="control-1"/>
            </xf:group>
          </xh:body>
        </xh:html>
      )

      val (_, newPart) = PartAnalysisBuilder.createPart(
        doc.staticState,
        parentPart,
        formWithDisallowedBindXml,
        dynamicScope
      )

      val errors = newPart.getStaticXPathErrors
      assert(errors.exists { case (_, throwableOpt, _) =>
        throwableOpt.exists { t =>
          val rootCause = Iterator.iterate[Throwable](t)(_.getCause).takeWhile(_ ne null).toList
          rootCause.exists(rc => rc.isInstanceOf[XPathException] && rc.getMessage.contains("is not allowed in user-defined form expressions"))
        }
      })
    }
  }
}
