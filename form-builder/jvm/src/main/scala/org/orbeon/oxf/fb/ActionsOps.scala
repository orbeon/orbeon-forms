package org.orbeon.oxf.fb

import org.orbeon.oxf.fb.FormBuilder.findNewActions
import org.orbeon.oxf.fb.XMLNames.{FBInstanceTest, FBSubmissionTest}
import org.orbeon.oxf.fr.XMLNames.FRServiceCallTest
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsNames.XFORMS_VAR_QNAME


trait ActionsOps {

  def renameServiceReferences(
    oldName          : String,
    newName          : String
  )(implicit
    ctx             : FormBuilderDocContext
  ): Unit = {

    val existingSubmissionId = s"$oldName-submission"
    val existingInstanceId   = s"$oldName-instance"

    val newSubmissionId = s"$newName-submission"
    val newInstanceId   = s"$newName-instance"

    val existingSubmissions = ctx.modelElem / FBSubmissionTest filter (_.id == existingSubmissionId)
    val existingInstances   = ctx.modelElem / FBInstanceTest   filter (_.id == existingInstanceId)

    // Rename submission and instance ids
    existingSubmissions.foreach(e => XFormsAPI.setvalue(e.idAtt, newSubmissionId))
    existingInstances  .foreach(e => XFormsAPI.setvalue(e.idAtt, newInstanceId))

    val legacyActions = FormBuilder.findLegacyActions(Some(ctx.modelElem))

    // Rename legacy service calls
    legacyActions                             descendant
      *                                       att
      ("submission" || "observer")            filter // covers both `observer` and `ev:observer`
      (_.stringValue == existingSubmissionId) foreach
      (att => XFormsAPI.setvalue(List(att), newSubmissionId))

    legacyActions descendant
      XFORMS_VAR_QNAME                                         filter
      (_.attValueOpt("name") contains "request-instance-name") flatMap
      (_.attOpt("value"))                                      filter
      (_.stringValue == s"'$existingInstanceId'")              foreach
      (valueAtt => XFormsAPI.setvalue(List(valueAtt), s"'$newInstanceId'"))

    // Rename action syntax service calls
    findNewActions(Some(ctx.modelElem)) descendant
      FRServiceCallTest          att
      "service"                  filter
      (_.stringValue == oldName) foreach
      (serviceAtt => XFormsAPI.setvalue(List(serviceAtt), newName))
  }
}
