package org.orbeon.oxf.fr.persistence.test

import org.orbeon.dom.Document
import org.orbeon.oxf.externalcontext.{Credentials, SafeRequestContext, UserAndGroup}
import org.orbeon.oxf.fr.FormRunnerPersistence.{DataXml, FormXhtml}
import org.orbeon.oxf.fr.persistence.api.PersistenceApi.headerFromRFC1123OrIso
import org.orbeon.oxf.fr.persistence.http.HttpCall.DefaultFormName
import org.orbeon.oxf.fr.persistence.http.{HttpAssert, HttpCall}
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.workflow.definitions20201.Stage
import org.orbeon.oxf.fr.{AppForm, Version}
import org.orbeon.oxf.http.{Headers, StatusCode}
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xml.dom.Converter.ScalaElemConverterOps

import java.time.Instant


object TestForm {
  def apply(
    provider  : Provider,
    controls  : Seq[TestForm.Control],
    formName  : String = DefaultFormName,
    operations: Option[String] = None
  ): TestForm =
    TestForm(
      appForm          = AppForm(provider.entryName, formName),
      titlesByLanguage = Map("en" -> formName),
      controls         = controls,
      operations       = operations
    )

  case class Control(label: String)

  private def controlName(index: Int) = s"control-${index + 1}"

  private def controlElem(index: Int, children: Seq[xml.Node]): xml.Elem =
    xml.Elem(
      prefix        = None.orNull,
      label         = controlName(index),
      attributes    = xml.Null,
      scope         = xml.TopScope,
      minimizeEmpty = true,
      child         = children*
    )
}

case class FormData(
  id                : String,
  singleControlValue: String,
  createdByOpt      : Option[String] = None,
  lastModifiedByOpt : Option[String] = None,
  workflowStageOpt  : Option[String] = None
)

case class FormDataDates(createdBy: Instant, lastModifiedBy: Instant)

case class TestForm(
  appForm         : AppForm,
  titlesByLanguage: Map[String, String],
  controls        : Seq[TestForm.Control],
  operations      : Option[String]
) {

  def controlPath(index: Int): String = s"section-1/${TestForm.controlName(index)}"

  def formDefinitionURL: String       = HttpCall.crudURLPrefix(appForm) + s"form/$FormXhtml"
  def formDataURL(id: String): String = HttpCall.crudURLPrefix(appForm) + s"data/$id/$DataXml"

  def putFormDefinition(
    version       : Version,
    credentials   : Option[Credentials] = None,
    update        : Boolean = false
  )(implicit
    logger        : IndentedLogger,
    safeRequestCtx: SafeRequestContext
  ): Unit =
    HttpAssert.put(
      url          = formDefinitionURL,
      version      = version,
      body         = HttpCall.XML(formDefinition),
      expectedCode = if (update) StatusCode.NoContent else StatusCode.Created,
      credentials  = credentials
    )

  def putFormData(
    version            : Version,
    formData           : Seq[FormData],
    returnDates        : Boolean = false
  )(implicit
    logger             : IndentedLogger,
    safeRequestCtx: SafeRequestContext
  ): Seq[FormDataDates] =
    formData.flatMap { formData =>
      putSingleFormData(
        version          = version,
        id               = formData.id,
        values           = Seq(formData.singleControlValue),
        createdByOpt     = formData.createdByOpt     .map(user => Credentials(UserAndGroup(user, None), Nil, Nil)),
        modifiedByOpt    = formData.lastModifiedByOpt.map(user => Credentials(UserAndGroup(user, None), Nil, Nil)),
        workflowStageOpt = formData.workflowStageOpt,
        returnDates      = returnDates
      )
    }

  def putSingleFormData(
    version            : Version,
    id                 : String,
    values             : Seq[String],
    createdByOpt       : Option[Credentials] = None,
    modifiedByOpt      : Option[Credentials] = None,
    workflowStageOpt   : Option[String] = None,
    returnDates        : Boolean = false
  )(implicit
    logger             : IndentedLogger,
    safeRequestCtx: SafeRequestContext
  ): Option[FormDataDates] = {
    val url            = formDataURL(id)
    val body           = HttpCall.XML(formData(values))
    val stageOpt       = workflowStageOpt.map(Stage( _, documentation = ""))
    val credentialsOpt = createdByOpt.orElse(modifiedByOpt)

    // Create
    HttpAssert.put(url, version, body, StatusCode.Created, credentialsOpt, stageOpt)

    modifiedByOpt.foreach { modifiedBy =>
      // Update
      HttpAssert.put(url, version, body, StatusCode.NoContent, Some(modifiedBy), stageOpt)
    }

    returnDates.option {
      val headers = HttpCall.get(url, version)._2

      val createdTime      = headerFromRFC1123OrIso(headers, Headers.OrbeonCreated, Headers.Created).get
      val lastModifiedTime = headerFromRFC1123OrIso(headers, Headers.OrbeonLastModified, Headers.LastModified).get

      FormDataDates(createdTime, lastModifiedTime)
    }
  }

  def formDefinition: Document =
    <xh:html
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:map="http://www.w3.org/2005/xpath-functions/map"
        xmlns:array="http://www.w3.org/2005/xpath-functions/array"
        xmlns:math="http://www.w3.org/2005/xpath-functions/math"
        xmlns:exf="http://www.exforms.org/exf/1-0"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:saxon="http://saxon.sf.net/"
        xmlns:sql="http://orbeon.org/oxf/xml/sql"
        xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:fb="http://orbeon.org/oxf/xml/form-builder">
      <xh:head>
        <xh:title>{titlesByLanguage.getOrElse("en", titlesByLanguage.headOption.getOrElse(""))}</xh:title>
        <xf:model id="fr-form-model" xxf:expose-xpath-types="true" xxf:analysis.calculate="true">
          <!-- Main instance -->
          <xf:instance id="fr-form-instance" xxf:exclude-result-prefixes="#all" xxf:index="id">
            <form>
              <section-1>
                <grid-1>{controls.indices.map(TestForm.controlElem(_, children = Seq()))}</grid-1>
              </section-1>
            </form>
          </xf:instance>
          <!-- Bindings -->
          <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
            <xf:bind id="section-1-bind" name="section-1" ref="section-1">
              <xf:bind id="grid-1-bind" ref="grid-1" name="grid-1">{
                controls.indices.map { index =>
                  val controlName = TestForm.controlName(index)
                  <xf:bind id={s"$controlName-bind"} name={controlName} ref={controlName}/>
                }
              }</xf:bind>
            </xf:bind>
          </xf:bind>
          <!-- Metadata -->
          <xf:instance id="fr-form-metadata" xxf:readonly="true" xxf:exclude-result-prefixes="#all">
            <metadata>
              <application-name>{appForm.app}</application-name>
              <form-name>{appForm.form}</form-name>{
                titlesByLanguage.map { case (language, title) =>
                  <title xml:lang={language}>{title}</title>
                }
              }
              <description xml:lang="en"></description>
              <created-with-version>2022.1-SNAPSHOT PE</created-with-version>{
                operations.toSeq.map { ops =>
                  <permissions>
                    <permission operations={ops}/>
                  </permissions>
                }
              }<email>
                <templates>
                  <template name="default">
                    <form-fields></form-fields>
                  </template>
                </templates>
                <parameters></parameters>
              </email>
              <grid-tab-order>default</grid-tab-order>
              <library-versions></library-versions>
              <updated-with-version>2022.1-SNAPSHOT PE</updated-with-version>
              <migration version="2019.1.0">{
                val content = controls.indices.map { index =>
                  val controlName = TestForm.controlName(index)
                  s"""{"value":"$controlName"}"""
                }.mkString(",")

                s"""{"migrations":[{"containerPath":[{"value":"section-1"}],"newGridElem":{"value":"grid-1"},"afterElem":null,"content":[$content],"topLevel":true}]}"""
              }</migration>
            </metadata>
          </xf:instance>
          <!-- Attachments -->
          <xf:instance id="fr-form-attachments" xxf:exclude-result-prefixes="#all">
            <attachments></attachments>
          </xf:instance>
          <!-- All form resources -->
          <xf:instance xxf:readonly="true" id="fr-form-resources" xxf:exclude-result-prefixes="#all">
            <resources>
              <resource xml:lang="en">
                <section-1>
                  <label>Untitled Section</label>
                </section-1>{
                  controls.zipWithIndex.map { case (control, index) =>
                    TestForm.controlElem(index, children = Seq(<label>{control.label}</label>, <hint/>))
                  }
              }</resource>
            </resources>
          </xf:instance>
        </xf:model>
      </xh:head>
      <xh:body>
        <fr:view>
          <fr:body
              xmlns:p="http://www.orbeon.com/oxf/pipeline"
              xmlns:xbl="http://www.w3.org/ns/xbl"
              xmlns:oxf="http://www.orbeon.com/oxf/processors">
            <fr:section id="section-1-section" bind="section-1-bind">
              <xf:label ref="$form-resources/section-1/label"></xf:label>
              <fr:grid id="grid-1-grid" bind="grid-1-bind">
                <fr:c y="1" x="1" w="6">{
                  controls.zipWithIndex.map { case (control, index) =>
                    val controlName = TestForm.controlName(index)

                    <xf:input id={s"$controlName-control"} bind={s"$controlName-bind"}>
                      <fr:index>
                        <fr:summary-show/>
                      </fr:index>
                      <xf:label ref={s"$$form-resources/$controlName/label"}/>
                      <xf:hint ref={s"$$form-resources/$controlName/hint"}/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  }
                }</fr:c>
                <fr:c y="1" x="7" w="6"/>
              </fr:grid>
            </fr:section>
          </fr:body>
        </fr:view>
      </xh:body>
    </xh:html>.toDocument

  def formData(values: Seq[String]): Document = {
    assert(controls.size == values.size)

    <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
      <section-1>{
        values.zipWithIndex.map { case (value, index) =>
          TestForm.controlElem(index, children = Seq(xml.Text(value)))
        }
      }</section-1>
    </form>.toDocument
  }
}
