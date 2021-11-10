package org.orbeon.oxf.fr.importexport

import org.orbeon.oxf.fr.SimpleDataMigration.FormOps
import org.orbeon.oxf.fr.{FormRunner, FormRunnerDocContext}
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsNames.{XFORMS_BIND_QNAME, XFORMS_INSTANCE_QNAME}


class FormDefinitionOps(form: om.NodeInfo) extends FormOps {

  type DocType   = om.DocumentInfo
  type BindType  = om.NodeInfo

  private val ctx = new FormRunnerDocContext {
    val formDefinitionRootElem: om.NodeInfo = form.rootElement
  }

  def findFormBindsRoot: Option[BindType] =
    ctx.topLevelBindElem

  def templateIterationNamesToRootElems: Map[String, om.NodeInfo] =
    (
      for {
        instance         <- ctx.modelElem.child(XFORMS_INSTANCE_QNAME)
        instanceId       = instance.id
        if FormRunner.isTemplateId(instanceId)
        instanceRootElem <- instance.firstChildOpt(*)
      } yield
        instanceRootElem.localname -> instanceRootElem
    ).toMap

  def bindChildren(bind: BindType): List[BindType] =
    bind.child(XFORMS_BIND_QNAME).toList

  def bindNameOpt(bind: BindType): Option[String] =
    bind.attValueOpt("name")
}