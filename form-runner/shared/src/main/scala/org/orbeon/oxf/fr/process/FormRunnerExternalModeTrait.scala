package org.orbeon.oxf.fr.process

import org.orbeon.oxf.fr.{DataStatus, FormRunnerParams}
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr.definitions.FormRunnerDetailMode
import org.orbeon.oxf.fr.permission.Operations

import java.time.Instant


trait FormRunnerExternalModeTrait {

  case class PublicModeMetadata(
    appFormVersion      : AppFormVersion,
    documentId          : Option[String],
    mode                : FormRunnerDetailMode,
    lang                : String,
    embeddable          : Boolean,
  )

  case class PrivateModeMetadata(
    authorizedOperations: Option[Operations],
    workflowStage       : Option[String],
    created             : Option[Instant],
    lastModified        : Option[Instant],
    eTag                : Option[String],
    dataStatus          : DataStatus,
  )

  case class ModeState(
    data                : Array[Byte],
    publicMetadata      : PublicModeMetadata,
    privateMetadata     : PrivateModeMetadata,
  )

  def createTokenAndStoreState(
    modeState         : ModeState,
    expirationDuration: java.time.Duration
  )(implicit
    formRunnerParams: FormRunnerParams
  ): String
}
