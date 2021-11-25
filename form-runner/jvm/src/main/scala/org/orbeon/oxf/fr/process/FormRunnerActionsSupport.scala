/**
  * Copyright (C) 2018 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.fr.process

import org.orbeon.oxf.fr.FormRunner.FormVersionParam
import org.orbeon.oxf.fr.FormRunnerPersistence._
import org.orbeon.oxf.fr.Names
import org.orbeon.oxf.util.PathUtils.{recombineQuery, splitQueryDecodeParams}

trait ProcessParams {
  def runningProcessId  : String
  def app               : String
  def form              : String
  def formVersion       : Int
  def document          : String
  def valid             : Boolean
  def language          : String
  def dataFormatVersion : String
  def workflowStage     : String
}

object FormRunnerActionsSupport {

  private def paramsToAppend(processParams: ProcessParams, paramNames: List[String]): List[(String, String)] =
    paramNames collect {
      case name @ "process"             => name -> processParams.runningProcessId
      case name @ "app"                 => name -> processParams.app
      case name @ "form"                => name -> processParams.form
      case name @ FormVersionParam      => name -> processParams.formVersion.toString
      case name @ "document"            => name -> processParams.document
      case name @ "valid"               => name -> processParams.valid.toString
      case name @ "language"            => name -> processParams.language
      case name @ DataFormatVersionName => name -> processParams.dataFormatVersion
      case name @ Names.WorkflowStage   => name -> processParams.workflowStage
    }

  def updateUriWithParams(processParams: ProcessParams, uri: String, requestedParamNames: List[String]): String = {

    val (path, params) = splitQueryDecodeParams(uri)

    val incomingParamNames = (params map (_._1)).toSet

    // Give priority to parameters on the URI, see:
    // https://github.com/orbeon/orbeon-forms/issues/3861
    recombineQuery(path, paramsToAppend(processParams, requestedParamNames filterNot incomingParamNames) ::: params)
  }
}
