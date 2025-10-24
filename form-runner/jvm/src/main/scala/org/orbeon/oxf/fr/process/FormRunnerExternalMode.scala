package org.orbeon.oxf.fr.process

import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.cache.CacheSupport
import org.orbeon.oxf.common.Defaults
import org.orbeon.oxf.fr.definitions.FormRunnerDetailMode
import org.orbeon.oxf.fr.permission.Operations
import org.orbeon.oxf.fr.{FormRunnerExternalModeToken, FormRunnerParams}
import org.orbeon.oxf.util.SLF4JLogging.*
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.{LoggerFactory, SecureUtils}
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.slf4j

import java.nio.charset.StandardCharsets
import java.time.Instant
import scala.util.Try
import scala.util.control.NonFatal


object FormRunnerExternalMode extends FormRunnerExternalModeTrait {

  val Logger = LoggerFactory.createLogger(FormRunnerExternalMode.getClass)
  implicit val Slf4JLogger: slf4j.Logger = Logger.logger

  private lazy val externalModeStore = CacheSupport.getOrElseThrow("form-runner.external-mode-store", store = true)

  import io.circe.generic.auto.*
  import io.circe.syntax.*

  def saveState(
    data                      : DocumentNodeInfoType,
    currentLang               : String,
    embeddable                : Boolean,
    continuationMode          : FormRunnerDetailMode,
    continuationWorkflowStage : Option[String],
    authorizedOperationsString: String,
    created                   : Option[Instant],
    lastModified              : Option[Instant],
    eTag                      : Option[String],
    expirationDuration        : java.time.Duration
  )(implicit
    formRunnerParams          : FormRunnerParams
  ): String =
    createTokenAndStoreState(
       ModeState(
        data = XFormsCrossPlatformSupport.serializeTinyTreeToByteArray(
          document           = data,
          method             = "xml",
          encoding           = Defaults.DefaultEncodingForModernUse,
          versionOpt         = None,
          indent             = false,
          omitXmlDeclaration = true,
          standaloneOpt      = None
        ),
        publicMetadata = PublicModeMetadata(
          appFormVersion = formRunnerParams.appFormVersion,
          documentId     = formRunnerParams.document,
          mode           = continuationMode,
          lang           = currentLang,
          embeddable     = embeddable,
        ),
        privateMetadata = PrivateModeMetadata(
          authorizedOperations = Operations.parseFromString(authorizedOperationsString),
          workflowStage        = continuationWorkflowStage,
          created              = created,
          lastModified         = lastModified,
          eTag                 = eTag,
        )
      ),
      expirationDuration
    )

  def encryptPrivateModeMetadata(
    privateModeMetadata: PrivateModeMetadata
  ): String =
    SecureUtils.encrypt(
      SecureUtils.KeyUsage.GeneralNoCheck,
      privateModeMetadata.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
    )

  def decryptPrivateModeMetadata(
    encryptedPrivateModeMetadata: String
  ): Try[PrivateModeMetadata] = {

    val decryptedBytes =
      SecureUtils.decrypt(
        SecureUtils.KeyUsage.GeneralNoCheck,
        encryptedPrivateModeMetadata
      )

    val jsonString =
      new String(decryptedBytes, StandardCharsets.UTF_8)

    io.circe.parser.decode[PrivateModeMetadata](jsonString).toTry
  }

  def createTokenAndStoreState(
    modeState         : ModeState,
    expirationDuration: java.time.Duration
  )(implicit
    formRunnerParams: FormRunnerParams
  ): String = {

    val token =
      FormRunnerExternalModeToken.encryptToken(
        app         = formRunnerParams.app,
        form        = formRunnerParams.form,
        version     = formRunnerParams.formVersion,
        documentOpt = formRunnerParams.document,
        validity    = expirationDuration,
      ).getOrElse(throw new IllegalStateException("could not create external mode token"))

    val jsonModeState =
      modeState.asJson

    debug(s"storing external mode state for token `$token`:\n${jsonModeState.spaces2}")

    // Store state in cache indexed by the token
    // Data in particular can be confidential, so we encrypt it before storing it
    externalModeStore.put(
      token,
      SecureUtils.encrypt(
        SecureUtils.KeyUsage.GeneralNoCheck,
        jsonModeState.noSpaces.getBytes(StandardCharsets.UTF_8)
      )
    )

    token
  }

  def retrieveStateForToken(token: String): Option[ModeState] =
    for {
      modeStateEncryptedSerializable <- externalModeStore.get(token)
      modeStateEncryptedBase64       = modeStateEncryptedSerializable.asInstanceOf[String]
      modeStateBytesDecrypted        = SecureUtils.decrypt(SecureUtils.KeyUsage.GeneralNoCheck, modeStateEncryptedBase64)
      modeStateJsonString            = new String(modeStateBytesDecrypted, StandardCharsets.UTF_8)
      modeState                      <- io.circe.parser.decode[ModeState](modeStateJsonString).toOption
    } yield
      modeState

  def removeStateForTokenDoNotThrow(token: String): Unit =
    try {
      externalModeStore.remove(token)
    } catch {
      case NonFatal(t) => error(s"error removing state: ${OrbeonFormatter.getThrowableMessage(t)}")
    }
}
