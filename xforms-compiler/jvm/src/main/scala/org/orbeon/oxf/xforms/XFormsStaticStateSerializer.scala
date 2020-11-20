package org.orbeon.oxf.xforms

import io.circe.syntax._
import io.circe.{Encoder, Json}
import org.orbeon.oxf.xforms.analysis.controls.AttributeControl
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, LangRef, TopLevelPartAnalysis}
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping
import org.orbeon.dom


object XFormsStaticStateSerializer {

  import io.circe.generic.semiauto._

  implicit val encodeSAXStore: Encoder[SAXStore] = (a: SAXStore) => Json.obj(
    "foo" -> Json.fromString("bar")
  )

  implicit val encodeScope: Encoder[Scope] = (a: Scope) => Json.obj(
    "parent"  -> None.asJson, // must be parent scope id TODO
    "scopeId" -> Json.fromString(a.scopeId),
    "idMap"   -> a.idMap.asJson
  )

  implicit val encodeAttributeControl: Encoder[AttributeControl] = (a: AttributeControl) => Json.obj(
    "foo" -> Json.fromString("bar")
  )

  implicit val encodeAnnotatedTemplate: Encoder[AnnotatedTemplate] = deriveEncoder
  implicit val encodeLangRef: Encoder[LangRef] = deriveEncoder

  implicit val encodeNamespaceMapping: Encoder[NamespaceMapping] = (a: NamespaceMapping) => Json.obj(
    "hash" -> Json.fromString(a.hash),
    "mapping" -> a.mapping.asJson
  )

  implicit val encodeNamespace: Encoder[dom.Namespace] = deriveEncoder

  // TODO: doesn't work because of `private` case class constructor
  implicit val encodeQName: Encoder[dom.QName] = deriveEncoder

  implicit val encodeElement: Encoder[dom.Element] = (a: dom.Element) => Json.obj(
    "atts" -> (a.attributeIterator map (a => a.getQName) toList).asJson
  )

  implicit val encodeElementAnalysis: Encoder[ElementAnalysis] = (a: ElementAnalysis) => Json.obj(
    "index"             -> Json.fromInt(a.index),
    "element"           -> a.element.asJson,
    "staticId"          -> Json.fromString(a.staticId),
    "prefixedId"        -> Json.fromString(a.prefixedId),
    "namespaceMapping"  -> a.namespaceMapping.asJson,
    "scopeRef"          -> Json.fromString(a.scope.scopeId),
    "containerScopeRef" -> Json.fromString(a.containerScope.scopeId),
    "modelRef"          -> (a.model map (_.prefixedId) map Json.fromString).asJson,
    "langRef"           -> a.lang.asJson
  )

  implicit val encodeTopLevelPartAnalysis: Encoder[TopLevelPartAnalysis] = (a: TopLevelPartAnalysis) => Json.obj(
    "startScope"         -> a.startScope.asJson,
    "controlAnalysisMap" -> a.controlAnalysisMap.asJson
  )

  implicit val encodeXFormsStaticState: Encoder[XFormsStaticState] = (a: XFormsStaticState) => Json.obj(
    "topLevelPart" -> a.topLevelPart.asJson,
    "template"     -> a.template.asJson,
  )

  def serialize(staticState: XFormsStaticState): String =
    staticState.asJson.spaces2
//    staticState.asJson.noSpaces
}
