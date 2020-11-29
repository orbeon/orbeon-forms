package org.orbeon.oxf.xforms

import org.orbeon.dom
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.test.{PipelineSupport, ResourceManagerSupport}
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.orbeon.oxf.xml.dom.Converter._
import org.scalatest.funspec.AnyFunSpecLike


class XFormsStaticStateSerializerTest
  extends ResourceManagerSupport
     with AnyFunSpecLike {

  ResourceManagerSupport

  describe("State serialization") {

    val HelloForm: dom.Document =
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms">
          <xh:head>
              <xh:title>Hello</xh:title>
              <xf:model>
                  <xf:instance>
                      <first-name xmlns=""/>
                  </xf:instance>
              </xf:model>
          </xh:head>
          <xh:body>
              <xh:p>
                  <xf:input ref="/*" incremental="true">
                      <xf:label>Please enter your first name:</xf:label>
                  </xf:input>
              </xh:p>
              <xh:p>
                  <xf:output value="concat('Hello, ', string(.), '!')"/>
                  <!--<xf:output value="if (normalize-space(string(.)) = '') then '' else concat('Hello, ', string(.), '!')"/>-->
              </xh:p>
          </xh:body>
      </xh:html>.toDocument

    val MultipleFieldsForm: dom.Document =
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms">
          <xh:head>
              <xh:title>Multiple Fields</xh:title>
              <xf:model>
                  <xf:instance>
                      <_ xmlns="">
                          <control-1/>
                          <control-2/>
                          <control-3/>
                      </_>
                  </xf:instance>
                  <xf:bind ref="*[3]" relevant="normalize-space(../*[1])"/>
              </xf:model>
          </xh:head>
          <xh:body>
              <xh:p>
                  <xf:input ref="*[1]">
                      <xf:label>Control 1:</xf:label>
                  </xf:input>
                  <xf:input ref="*[2]">
                      <xf:label>Control 2:</xf:label>
                  </xf:input>
                  <xf:input ref="*[3]">
                      <xf:label>Control 3:</xf:label>
                  </xf:input>
              </xh:p>
              <xh:p>
                  <xf:output value="string-join((*[1]/string(), *[2]/string()), ' and ')">
                      <xf:label>Result:</xf:label>
                  </xf:output>
              </xh:p>
          </xh:body>
      </xh:html>.toDocument

    it("must serialize without errors") {
      withTestExternalContext { _ =>

//        val (template, staticState) = PartAnalysisBuilder.createFromDocument(HelloForm)
        val (template, staticState) = PartAnalysisBuilder.createFromDocument(MultipleFieldsForm)

        val jsonString = XFormsStaticStateSerializer.serialize(template, staticState)

        println(s"xxxx $jsonString")
      }
    }
  }

  def withTestExternalContext[T](body: ExternalContext => T): T =
    InitUtils.withPipelineContext { pipelineContext =>
      body(
        PipelineSupport.setExternalContext(
          pipelineContext,
          PipelineSupport.DefaultRequestUrl
        )
      )
    }
}
