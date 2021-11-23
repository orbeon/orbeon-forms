package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.datamigration.PathElem
import org.orbeon.oxf.fr.process.SimpleProcessCommon
import org.orbeon.saxon.om.NodeInfo


trait FormRunnerCommon
extends FormRunnerPersistence
   with FormRunnerPermissionsOps
   with FormRunnerEmail
   with FormRunnerLang
   with FormRunnerBaseOps
   with FormRunnerControlOps
   with FormRunnerContainerOps
   with FormRunnerSectionTemplateOps
   with FormRunnerActionsOps
   with FormRunnerResourcesOps

object FormRunnerCommon {

  // Extensible records would be cool here. see:
  //
  // - https://github.com/lampepfl/dotty/issues/964
  // - https://github.com/milessabin/shapeless/wiki/Feature-overview:-shapeless-2.0.0#extensible-records
  //
  case class BindPath                       (                   bind: NodeInfo, path: List[PathElem]                         )
  case class BindPathHolders                (                   bind: NodeInfo, path: List[PathElem], holders: Option[List[NodeInfo]])
  case class ControlBindPathHoldersResources(control: NodeInfo, bind: NodeInfo, path: List[PathElem], holders: Option[List[NodeInfo]], resources: Seq[(String, NodeInfo)])

  // Do this to avoid IntelliJ failing to see the specific `FormRunner` instance
  @inline def frc: FormRunnerCommon    = org.orbeon.oxf.fr.FormRunner
  @inline def spc: SimpleProcessCommon = org.orbeon.oxf.fr.process.SimpleProcess
}