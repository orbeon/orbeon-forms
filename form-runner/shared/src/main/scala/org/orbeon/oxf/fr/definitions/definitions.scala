package org.orbeon.oxf.fr.definitions

import io.circe.syntax.EncoderOps
import io.circe.*
import org.orbeon.dom.{Namespace, QName}
import org.orbeon.oxf.fr.permission.Operations


sealed trait ModeType

object ModeType {

  trait ForExistingData extends ModeType // `type ForExistingData = Edition | Readonly`

  case object Creation extends ModeType
  case object Edition  extends ModeType with ForExistingData
  case object Readonly extends ModeType with ForExistingData

  private val (primaryModes: Map[String, FormRunnerDetailMode], secondaryModes: Map[String, FormRunnerDetailMode.Secondary]) =
    (
      FormRunnerDetailMode.PrimaryModes
        .map(m => m.name.qualifiedName -> m)
        .toMap,
      FormRunnerDetailMode.SecondaryModes
        .map(m => m.name.qualifiedName -> m)
        .toMap
    )

  private val primaryAndSecondaryModes = `primaryModes` ++ `secondaryModes`

  def fromModeTypeString(modeTypeString: String): Option[ModeType] = modeTypeString match {
    case "creation" => Some(Creation)
    case "edition"  => Some(Edition)
    case "readonly" => Some(Readonly)
    case _          => None
  }

  def modeTypeFromQualifiedName(
    modeQualifiedName    : String,
    excludeSecondaryModes: Boolean,
    customModes          : Iterable[FormRunnerDetailMode.Custom]
  ): Option[ModeType] =
    modeFromQualifiedName(modeQualifiedName, excludeSecondaryModes, customModes).map(_.modeType)

  def modeFromQualifiedName(
    modeQualifiedName    : String,
    excludeSecondaryModes: Boolean,
    customModes          : Iterable[FormRunnerDetailMode.Custom]
  ): Option[FormRunnerDetailMode] =
    (if (excludeSecondaryModes) primaryModes else primaryAndSecondaryModes).get(modeQualifiedName)
      .orElse(customModes.find(_.name.qualifiedName == modeQualifiedName))
}

case class ModeTypeAndOps(modeType: ModeType.ForExistingData, ops: Operations)

sealed trait FormRunnerDetailMode {
  val name       : QName
  val modeType   : ModeType
  val persistence: Boolean
}

object FormRunnerDetailMode {

  def isSupportedNonDetailMode(mode: String): Boolean =
    Set("summary", "home", "landing", "validate", "import").contains(mode) // xxx constants

  case object New  extends FormRunnerDetailMode { val name = QName("new");  val modeType = ModeType.Creation; val persistence = true }
  case object Edit extends FormRunnerDetailMode { val name = QName("edit"); val modeType = ModeType.Edition;  val persistence = true }
  case object View extends FormRunnerDetailMode { val name = QName("view"); val modeType = ModeType.Readonly; val persistence = true }
  case object Pdf  extends FormRunnerDetailMode { val name = QName("pdf");  val modeType = ModeType.Readonly; val persistence = true }

  case class Secondary(name: QName, modeType: ModeType, persistence: Boolean) extends FormRunnerDetailMode {
    require(! PrimaryModes.exists(_.name == name))
  }

  case class Custom(name: QName, modeType: ModeType, persistence: Boolean) extends FormRunnerDetailMode {
    require(! PrimaryModes.exists(_.name == name))
    require(name.namespace.prefix.nonEmpty)
  }

  val PrimaryModes: Set[FormRunnerDetailMode] = Set(New, Edit, View, Pdf)

  val SecondaryModes: Set[FormRunnerDetailMode.Secondary] = {

    // TODO: What about `compile`? Is it a mode? There is one use of `'compile'` in `components.xsl`.

    val secondaryCreationModes =
      List(
        "import",
        "validate",
        "test"
      )
      .map(n => Secondary(QName(n), ModeType.Creation, persistence = true))

    val secondaryReadonlyModes =
      List(
        "tiff",         // reduced to `pdf` at the XForms level, but not at the XSLT level
        "test-pdf",     // reduced to `pdf` at the XForms level, but not at the XSLT level
        "controls",
        "schema",       // 2021-12-22: could be a readonly mode, but we consider this special as it is protected as a service.
        "export",
        "excel-export", // legacy
      )
      .map(n => Secondary(QName(n), ModeType.Readonly, persistence = true))

    (secondaryCreationModes ++ secondaryReadonlyModes).toSet
  }

  // TODO: Move `QName` and `Namespace` encoders/decoders; but they need to be explicitly imported, as we don't want
  //  them to be used by the `XFormsStaticState` encoders/decoders.
  implicit val encodeNamespace: Encoder[Namespace] = (v: Namespace) =>
    Json.obj(
      ("prefix", Json.fromString(v.prefix)),
      ("uri",    Json.fromString(v.uri))
    )

  implicit val decodeNamespace: Decoder[Namespace] = (c: HCursor) =>
    for {
      prefix <- c.downField("prefix").as[String]
      uri    <- c.downField("uri").as[String]
    } yield Namespace(prefix, uri)

  implicit val encodeQName: Encoder[QName] = (v: QName) =>
    Json.obj(
      ("localName", Json.fromString(v.localName)),
      ("namespace", v.namespace.asJson),
      ("qualifiedName", Json.fromString(v.qualifiedName))
    )

  implicit val decodeQName: Decoder[QName] = (c: HCursor) =>
    for {
      localName     <- c.downField("localName").as[String]
      namespace     <- c.downField("namespace").as[Namespace]
      qualifiedName <- c.downField("qualifiedName").as[String]
    } yield QName(localName, namespace, qualifiedName)

  implicit val encode: Encoder[FormRunnerDetailMode] = (m: FormRunnerDetailMode) =>
    Json.obj(
      ("name",        m.name.asJson),
      ("modeType",    Json.fromString(m.modeType match {
        case ModeType.Creation => "creation"
        case ModeType.Edition  => "edition"
        case ModeType.Readonly => "readonly"
      })),
      ("persistence", Json.fromBoolean(m.persistence))
    )

  implicit val decode: Decoder[FormRunnerDetailMode] = (c: HCursor) =>
    for {
      name        <- c.downField("name").as[QName]
      modeTypeStr <- c.downField("modeType").as[String]
      modeType    <- ModeType.fromModeTypeString(modeTypeStr).toRight(DecodingFailure(s"invalid mode type: $modeTypeStr", c.history))
      persistence <- c.downField("persistence").as[Boolean]
    } yield
      FormRunnerDetailMode.PrimaryModes.find(_.name == name)
        .orElse(FormRunnerDetailMode.SecondaryModes.find(_.name == name))
        .getOrElse(FormRunnerDetailMode.Custom(name, modeType, persistence))
}
