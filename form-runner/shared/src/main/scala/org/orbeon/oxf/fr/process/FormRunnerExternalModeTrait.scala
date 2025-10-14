package org.orbeon.oxf.fr.process

import org.orbeon.oxf.fr.FormRunnerParams
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr.definitions.FormRunnerDetailMode
import org.orbeon.oxf.fr.permission.Operations

import java.time.Instant


trait FormRunnerExternalModeTrait {

  case class ModeMetadata(
    appFormVersion      : AppFormVersion,
    documentId          : Option[String],
    mode                : FormRunnerDetailMode,
    authorizedOperations: Option[Operations],
    workflowStage       : Option[String],
    created             : Option[Instant],
    lastModified        : Option[Instant],
    eTag                : Option[String],
    lang                : String,
    embeddable          : Boolean,
    //dataSafe            : Boolean, // later
  )

  case class ModeState(
    data    : Array[Byte],
    metadata: ModeMetadata,
  )

  def createTokenAndStoreState(
    modeState         : ModeState,
    expirationDuration: java.time.Duration
  )(implicit
    formRunnerParams: FormRunnerParams
  ): String
}
