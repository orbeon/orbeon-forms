package org.orbeon.oxf.xforms

import io.circe.syntax._
import io.circe.{Encoder, Json}
import io.circe.generic.semiauto._
import org.orbeon.oxf.xforms.analysis.controls.{AttributeControl, InputControl, LHHAAnalysis, OutputControl}
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, LangRef, TopLevelPartAnalysis, WithChildrenTrait}
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping
import org.orbeon.dom
import org.orbeon.oxf.http.BasicCredentials
import org.orbeon.oxf.xforms.analysis.model.{Instance, Model}

import scala.jdk.CollectionConverters._


object XFormsStaticStateSerializer {

  implicit val encodeSAXStore: Encoder[SAXStore] = (a: SAXStore) => {

    implicit val encodeSAXStoreMark: Encoder[a.Mark] = (m: a.Mark) => Json.obj(
      "_id"                          -> Json.fromString(m._id),
      "eventBufferPosition"          -> Json.fromInt(m.eventBufferPosition),
      "charBufferPosition"           -> Json.fromInt(m.charBufferPosition),
      "intBufferPosition"            -> Json.fromInt(m.intBufferPosition),
      "lineBufferPosition"           -> Json.fromInt(m.lineBufferPosition),
      "systemIdBufferPosition"       -> Json.fromInt(m.systemIdBufferPosition),
      "attributeCountBufferPosition" -> Json.fromInt(m.attributeCountBufferPosition),
      "stringBuilderPosition"        -> Json.fromInt(m.stringBuilderPosition)
    )

    Json.obj(

      "eventBufferPosition"          -> Json.fromInt(a.eventBufferPosition),
      "eventBuffer"                  -> a.eventBuffer.slice(0, a.eventBufferPosition).asJson,
      "charBufferPosition"           -> Json.fromInt(a.charBufferPosition),
      "charBuffer"                   -> a.charBuffer.slice(0, a.charBufferPosition).asJson,
      "intBufferPosition"            -> Json.fromInt(a.intBufferPosition),
      "intBuffer"                    -> a.intBuffer.slice(0, a.intBufferPosition).asJson,
      "lineBufferPosition"           -> Json.fromInt(a.lineBufferPosition),
      "lineBuffer"                   -> a.lineBuffer.slice(0, a.lineBufferPosition).asJson,
      "systemIdBufferPosition"       -> Json.fromInt(a.systemIdBufferPosition),
      "systemIdBuffer"               -> a.systemIdBuffer.slice(0, a.systemIdBufferPosition).map(x => if (x eq null) "" else x).asJson,
      "attributeCountBufferPosition" -> Json.fromInt(a.attributeCountBufferPosition),
      "attributeCountBuffer"         -> a.attributeCountBuffer.slice(0, a.attributeCountBufferPosition).asJson,
      "attributeCount"               -> Json.fromInt(a.attributeCount),
      "stringBuilder"                -> a.stringBuilder.asScala.asJson,
      "hasDocumentLocator"           -> Json.fromBoolean(a.hasDocumentLocator),
      //    write(out, if (a.publicId == null) "" else a.publicId)
      "marks"                        -> Option(a.marks).map(_.asScala).getOrElse(Nil).asJson
    )
  }

  implicit val encodeAnnotatedTemplate : Encoder[AnnotatedTemplate] = deriveEncoder

  def serialize(staticState: XFormsStaticState): String = {

    implicit val encodeScope: Encoder[Scope] = (a: Scope) => Json.obj(
      "parent"  -> None.asJson, // must be parent scope id TODO
      "scopeId" -> Json.fromString(a.scopeId),
      "idMap"   -> a.idMap.asJson
    )

    implicit val encodeAttributeControl: Encoder[AttributeControl] = (a: AttributeControl) => Json.obj(
      "foo" -> Json.fromString("bar")
    )

    implicit val encodeLangRef           : Encoder[LangRef]           = deriveEncoder
    implicit val encodeNamespace         : Encoder[dom.Namespace]     = deriveEncoder
    implicit val encodeBasicCredentials  : Encoder[BasicCredentials]  = deriveEncoder

    implicit val encodeNamespaceMapping: Encoder[NamespaceMapping] = (a: NamespaceMapping) => Json.obj(
      "hash" -> Json.fromString(a.hash),
      "mapping" -> a.mapping.asJson
    )

    // NOTE: `deriveEncoder` doesn't work because of `private` case class constructor.
    implicit val encodeQName: Encoder[dom.QName] = (a: dom.QName) => Json.obj(
      "localName"          -> Json.fromString(a.localName),
      "prefix"             -> Json.fromString(a.namespace.prefix),
      "uri"                -> Json.fromString(a.namespace.uri),
    )

    // TODO: Just for local element name/attributes, so find another way
  //  implicit val encodeElement: Encoder[dom.Element] = (a: dom.Element) => Json.obj(
  //    "atts" -> (a.attributeIterator map (a => a.getQName) toList).asJson
  //  )

  //  implicit val encodeElement: Encoder[dom.Element] = (a: dom.Element) =>
  //    Json.fromString(TransformerUtils.dom(a))

    implicit val encodeElement: Encoder[dom.Element] = (a: dom.Element) => Json.obj(
      "name" -> a.getQName.asJson,
      "atts" -> (a.attributeIterator map (a => (a.getQName, a.getValue)) toList).asJson,
    )

    def maybeWithSpecificElementAnalysisFields(a: ElementAnalysis): List[(String, Json)] =
      a match {
        case c: Model         => Nil // modelFields(c)
        case c: Instance      =>
          List(
            "readonly"              -> Json.fromBoolean(c.readonly),
            "cache"                 -> Json.fromBoolean(c.cache),
            "timeToLive"            -> Json.fromLong(c.timeToLive),
            "exposeXPathTypes"      -> Json.fromBoolean(c.exposeXPathTypes),
            "indexIds"              -> Json.fromBoolean(c.indexIds),
            "indexClasses"          -> Json.fromBoolean(c.indexClasses),
            "isLaxValidation"       -> Json.fromBoolean(c.isLaxValidation),
            "isStrictValidation"    -> Json.fromBoolean(c.isStrictValidation),
            "isSchemaValidation"    -> Json.fromBoolean(c.isSchemaValidation),
            "credentials"           -> c.credentials.asJson,
            "excludeResultPrefixes" -> c.excludeResultPrefixes.asJson,
            "useInlineContent"      -> Json.fromBoolean(c.useInlineContent),
            "useExternalContent"    -> Json.fromBoolean(c.useExternalContent),
            "instanceSource"        -> c.instanceSource.asJson,
            "inlineRootElem"        -> c.inlineRootElemOpt.asJson
          )
        case c: InputControl  => Nil // inputControlFields(c)
        case c: OutputControl => Nil // outputControlFields(c)
        case c: LHHAAnalysis  => Nil // lhhaAnalysisFields(c)
        case c                => Nil
      }

    implicit lazy val encodeElementAnalysis: Encoder[ElementAnalysis] = (a: ElementAnalysis) =>
      Json.fromFields(
        List(
          "index"             -> Json.fromInt(a.index),
          "name"              -> Json.fromString(a.localName),
          "element"           -> a.element.asJson,
          "staticId"          -> Json.fromString(a.staticId),
          "prefixedId"        -> Json.fromString(a.prefixedId),
          "namespaceMapping"  -> a.namespaceMapping.asJson,
          "scopeRef"          -> Json.fromString(a.scope.scopeId),
          "containerScopeRef" -> Json.fromString(a.containerScope.scopeId),
          "modelRef"          -> (a.model map (_.prefixedId) map Json.fromString).asJson,
          "langRef"           -> a.lang.asJson,
        ) ++
          maybeWithSpecificElementAnalysisFields(a) ++
          maybeWithChildrenFields(a)
      )

    def maybeWithChildrenFields(a: ElementAnalysis): List[(String, Json)] =
      a match {
        case w: WithChildrenTrait if w.children.nonEmpty => List("children" -> withChildrenEncoder(w))
        case _                                           => Nil
      }

    def withChildrenEncoder(a: WithChildrenTrait): Json = a.children.asJson

    implicit val encodeTopLevelPartAnalysis: Encoder[TopLevelPartAnalysis] = (a: TopLevelPartAnalysis) => Json.obj(
      "startScope"       -> a.startScope.asJson,
      "topLevelControls" -> a.getTopLevelControls.asJson
    )

    implicit val encodeXFormsStaticState: Encoder[XFormsStaticState] = (a: XFormsStaticState) => Json.obj(
      "nonDefaultProperties" -> a.nonDefaultProperties.asJson,
      "topLevelPart"         -> a.topLevelPart.asJson,
      "template"             -> a.template.asJson,
    )

    staticState.asJson.noSpaces
//    staticState.asJson.spaces2
  }
}
