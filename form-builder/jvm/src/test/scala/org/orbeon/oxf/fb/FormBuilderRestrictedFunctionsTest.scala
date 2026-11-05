package org.orbeon.oxf.fb

import org.orbeon.dom.{Document, QName}
import org.orbeon.oxf.fr.{FormRunner, FormRunnerSupport, NodeInfoCell}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.{IndentedLogger, StaticXPath}
import org.orbeon.oxf.xforms.NodeInfoFactory
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.orbeon.oxf.xforms.analysis.controls.AttributeControl
import org.orbeon.oxf.xforms.control.{XFormsContainerControl, XFormsControl}
import org.orbeon.oxf.xforms.control.controls.{XFormsGroupControl, XFormsSelect1Control, XXFormsAttributeControl, XXFormsDynamicControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.saxon.trans.XPathException
import org.orbeon.scaxon.NodeConversions
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.xbl.Scope
import org.scalatest.funspec.AnyFunSpecLike

class FormBuilderRestrictedFunctionsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport
     with FormBuilderSupport {

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

  describe("Form Builder Cell Badges (#3109)") {

    it("must update cell badges when control formulas and MIPs change") {

      def findBadgesGroup(root: XFormsControl): Option[XFormsGroupControl] =
        Iterator.iterate(List(root))(_.flatMap {
          case c: org.orbeon.oxf.xforms.control.XFormsContainerControl => c.children
          case _                                                       => Nil
        }).takeWhile(_.nonEmpty).flatten.collectFirst {
          case g: XFormsGroupControl if g.staticControl.element.attributeValue("class") == "fb-control-cell-badges" => g
        }

      def childAttributeValues(group: XFormsGroupControl, attrName: String): List[String] =
        group.children.collect {
          case a: XXFormsAttributeControl if a.staticControl.asInstanceOf[AttributeControl].attributeName == attrName =>
            a.getValue(EventCollector.Throw)
        }.toList

      val fbDocWithDynamic: Document = {
        val doc = formBuilderDoc(TemplateWithSingleControlDoc)
        val body = doc.getRootElement.element("body")
        val dynamicElem = NodeConversions.elemToOrbeonDom(
          <xxf:dynamic xmlns:xxf="http://orbeon.org/oxf/xml/xforms" id="fb" ref="instance('fb-form-instance')"/>
        ).getRootElement.detach()
        body.add(dynamicElem)
        doc
      }

      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(fbDocWithDynamic)) {
          val fbDynamicControl = resolveObject[XXFormsDynamicControl]("fb").get

          // Initially `control-1` has no formulas or constraints, so the badges group is non-relevant
          val badgesGroup0 = findBadgesGroup(fbDynamicControl)
          assert(badgesGroup0.isDefined)
          assert(! badgesGroup0.get.isRelevant)

          // Set a Calculated Value on `control-1`
          FormBuilderXPathApi.setControlCalculatedMipFromToolbar("control-1", "calculate", "40 + 2")
          XFormsAPI.refresh("fr-form-model")

          val badgesGroup1 = findBadgesGroup(fbDynamicControl).get
          assert(badgesGroup1.isRelevant)

          val titles1  = childAttributeValues(badgesGroup1, "title")
          val classes1 = childAttributeValues(badgesGroup1, "class")

          assert(titles1.contains("Calculated Value: 40 + 2"))
          assert(titles1.find(_.startsWith("Read-Only:")).isDefined)
          assert(classes1.exists(c => c.contains("fb-badge-calculate") && ! c.contains("xforms-hidden")))
          assert(classes1.exists(c => c.contains("fb-badge-readonly")  && ! c.contains("xforms-hidden")))
          assert(classes1.exists(c => c.contains("fb-badge-default")   && c.contains("xforms-hidden")))

          // Set an Initial Value, make Visibility false(), and clear Calculated Value
          FormBuilderXPathApi.setControlCalculatedMipFromToolbar("control-1", "default", "'hello'")
          FormBuilderXPathApi.setControlCalculatedMipFromToolbar("control-1", "relevant", "false()")
          FormBuilderXPathApi.setControlCalculatedMipFromToolbar("control-1", "calculate", "")
          XFormsAPI.refresh("fr-form-model")

          val badgesGroup2 = findBadgesGroup(fbDynamicControl).get
          assert(badgesGroup2.isRelevant)

          val titles2  = childAttributeValues(badgesGroup2, "title")
          val classes2 = childAttributeValues(badgesGroup2, "class")

          assert(titles2.contains("Initial Value: 'hello'"))
          assert(titles2.find(_.startsWith("Visibility:")).isDefined)
          assert(classes2.exists(c => c.contains("fb-badge-default")    && ! c.contains("xforms-hidden")))
          assert(classes2.exists(c => c.contains("fb-badge-visibility") && ! c.contains("xforms-hidden")))
          assert(classes2.exists(c => c.contains("fb-badge-calculate")  && c.contains("xforms-hidden")))
        }
      }
    }

    it("must populate years itemset when switching fr:year appearance to fr:dropdown-year") {

      val fbDocWithDynamic: Document = {
        val doc = formBuilderDoc(TemplateWithSingleControlDoc)
        val body = doc.getRootElement.element("body")
        val dynamicElem = NodeConversions.elemToOrbeonDom(
          <xxf:dynamic xmlns:xxf="http://orbeon.org/oxf/xml/xforms" id="fb" ref="instance('fb-form-instance')"/>
        ).getRootElement.detach()
        body.add(dynamicElem)
        doc
      }

      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(fbDocWithDynamic)) {
          implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

          val firstTd = FormBuilder.findSelectedCell.orElse((ctx.bodyElem descendant NodeInfoCell.GridTest descendant NodeInfoCell.CellTest).headOption).get
          FormBuilder.selectCell(firstTd)

          val yearBinding = ctx.bindingIndex.iterateDescriptors.flatMap(_.binding).find(_.attValueOpt("id").contains("fr-year")).get
          val yearName    = ToolboxOps.insertNewControl(yearBinding).get
          XFormsAPI.refresh("fr-form-model")

          FormBuilderXPathApi.setControlAppearanceFromToolbar(yearName, "dropdown")
          XFormsAPI.refresh("fr-form-model")

          val fbDynamicControl = resolveObject[XXFormsDynamicControl]("fb").get
          def findSelect1Labels: List[String] =
            Iterator.iterate(List[XFormsControl](fbDynamicControl))(_.flatMap {
              case c: XFormsContainerControl => c.children
              case _                         => Nil
            }).takeWhile(_.nonEmpty).flatten.collectFirst {
              case s: XFormsSelect1Control if s.effectiveId.contains(s"$yearName-control") =>
                s.getItemset(EventCollector.Throw).allItemsIterator.map(_.label.label).toList
            }.getOrElse(Nil)

          assert(findSelect1Labels.size > 1)

          val controlElem = FormRunner.findControlByName(yearName).get
          XFormsAPI.insert(into = List(controlElem), origin = List(NodeInfoFactory.attributeInfo(QName("start-year"), "2020")))
          XFormsAPI.insert(into = List(controlElem), origin = List(NodeInfoFactory.attributeInfo(QName("end-year"),   "2022")))
          XFormsAPI.refresh("fr-form-model")

          assert(findSelect1Labels == List("", "2022", "2021", "2020"))
        }
      }
    }
  }
}
