/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.library

import org.orbeon.saxon.`type`.{BuiltInAtomicType, Type}
import org.orbeon.saxon.expr.StaticProperty._
import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.oxf.xforms.function.xxforms._
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.oxf.xforms.function.{Bind, Event, If, XXFormsValid}
import org.orbeon.oxf.xforms.function.exforms.EXFormsMIP
import org.orbeon.saxon.`type`.Type.{ITEM_TYPE, NODE_TYPE}

/*
 * Orbeon extension functions that depend on the XForms environment.
 */
trait XXFormsEnvFunctions extends OrbeonFunctionLibrary {

  // Define in early definition of subclass
  val XXFormsEnvFunctionsNS: Seq[String]

  Namespace(XXFormsEnvFunctionsNS) {

    // NOTE: This is deprecated and just points to the event() function.
    Fun("event", classOf[Event], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("cases", classOf[XXFormsCases], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("repeat-current", classOf[XXFormsRepeatCurrent], op = 0, min = 0, Type.NODE_TYPE, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("repeat-position", classOf[XXFormsRepeatPosition], op = 0, min = 0, INTEGER, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("repeat-positions", classOf[XXFormsRepeatPositions], op = 0, min = 0, INTEGER, ALLOWS_ZERO_OR_MORE)

    Fun("context", classOf[XXFormsContext], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("repeat-items", classOf[XXFormsRepeatItems], op = 0, min = 0, Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    // Backward compatibility, use repeat-items() instead
    Fun("repeat-nodeset", classOf[XXFormsRepeatItems], op = 0, min = 0, Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("evaluate-bind-property", classOf[XXFormsEvaluateBindProperty], op = 0, min = 2, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(ANY_ATOMIC, EXACTLY_ONE) // QName or String
    )

    Fun("valid", classOf[XXFormsValid], op = 0, min = 0, BOOLEAN, EXACTLY_ONE,
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(BOOLEAN, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("type", classOf[XXFormsType], op = 0, min = 0, QNAME, ALLOWS_ZERO_OR_MORE,
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("custom-mip", classOf[XXFormsCustomMIP], op = 0, min = 2, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("invalid-binds", classOf[XXFormsInvalidBinds], op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("if", classOf[If], op = 0, min = 3, STRING, EXACTLY_ONE,
      Arg(BOOLEAN, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("binding", classOf[XXFormsBinding], op = 0, min = 1, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("binding-context", classOf[XXFormsBindingContext], op = 0, min = 1, Type.ITEM_TYPE, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("is-control-relevant", classOf[XXFormsIsControlRelevant], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("is-control-readonly", classOf[XXFormsIsControlReadonly], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("is-control-required", classOf[XXFormsIsControlRequired], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("is-control-valid", classOf[XXFormsIsControlValid], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("value", classOf[XXFormsValue], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("formatted-value", classOf[XXFormsFormattedValue], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("avt-value", classOf[XXFormsAVTValue], op = 0, min = 2, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("component-context", classOf[XXFormsComponentContext], op = 0, min = 0, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)

    Fun("component-param-value", classOf[XXFormsComponentParam], op = 0, min = 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
        Arg(STRING, EXACTLY_ONE)
    )

    Fun("instance", classOf[XXFormsInstance], op = 0, min = 1, Type.NODE_TYPE, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("index", classOf[XXFormsIndex], op = 0, min = 0, INTEGER, EXACTLY_ONE,
      Arg(STRING, ALLOWS_ZERO_OR_ONE)
    )

    Fun("list-models", classOf[XXFormsListModels], op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE)

    Fun("list-instances", classOf[XXFormsListInstances], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("list-variables", classOf[XXFormsListVariables], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("get-variable", classOf[XXFormsGetVariable], op = 0, min = 2, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("itemset", classOf[XXFormsItemset], op = 0, min = 2, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("format-message", classOf[XXFormsFormatMessage], op = 0, min = 2, STRING, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("lang", classOf[XXFormsLang], op = 0, min = 0, STRING, ALLOWS_ZERO_OR_ONE)

    Fun("r", classOf[XXFormsResource], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(BuiltInAtomicType.ANY_ATOMIC, EXACTLY_ONE) // `map(*)`
    )

    Fun("resource-elements", classOf[XXFormsResourceElem], op = 0, min = 1, NODE_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("pending-uploads", classOf[XXFormsPendingUploads], op = 0, min = 0, INTEGER, EXACTLY_ONE)

    Fun("document-id", classOf[XXFormsDocumentId], op = 0, min = 0, STRING, EXACTLY_ONE)

    // TODO: This is the only place where we use `op`. Should remove it and remove the `op` from `Fun`.
    Fun("label", classOf[XXFormsLHHA], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )
    Fun("help", classOf[XXFormsLHHA], op = 1, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )
    Fun("hint", classOf[XXFormsLHHA], op = 2, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )
    Fun("alert", classOf[XXFormsLHHA], op = 3, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("visited", classOf[XXFormsVisited], op = 0, min = 1, BOOLEAN, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("focusable", classOf[XXFormsFocusable], op = 0, min = 1, BOOLEAN, ALLOWS_ZERO_OR_ONE,
      Arg(STRING,  EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("absolute-id", classOf[XXFormsAbsoluteId], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("client-id", classOf[XXFormsClientId], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("control-element", classOf[XXFormsControlElement], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("extract-document", classOf[XXFormsExtractDocument], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
      Arg(Type.NODE_TYPE, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    // RFE: Support XSLT 2.0-features such as multiple sort keys
    Fun("sort", classOf[XXFormsSort], op = 0, min = 2, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(Type.ITEM_TYPE, EXACTLY_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE)
    )

    // NOTE: also from exforms
    Fun("relevant", classOf[EXFormsMIP], op = 0, min = 0, BOOLEAN, EXACTLY_ONE,
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    // NOTE: also from exforms
    Fun("readonly", classOf[EXFormsMIP], op = 1, min = 0, BOOLEAN, EXACTLY_ONE,
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    // NOTE: also from exforms
    Fun("required", classOf[EXFormsMIP], op = 2, min = 0, BOOLEAN, EXACTLY_ONE,
      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    // Now available in XForms 2.0
    Fun("bind", classOf[Bind], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    // Validation functions
    Fun("max-length", classOf[MaxLengthValidation], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(INTEGER, ALLOWS_ZERO_OR_ONE)
    )

    Fun("min-length", classOf[MinLengthValidation], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(INTEGER, ALLOWS_ZERO_OR_ONE)
    )

    Fun("non-negative", classOf[NonNegativeValidation], op = 0, min = 0, BOOLEAN, EXACTLY_ONE)
    Fun("negative",     classOf[NegativeValidation],    op = 0, min = 0, BOOLEAN, EXACTLY_ONE)
    Fun("non-positive", classOf[NonPositiveValidation], op = 0, min = 0, BOOLEAN, EXACTLY_ONE)
    Fun("positive",     classOf[PositiveValidation],    op = 0, min = 0, BOOLEAN, EXACTLY_ONE)

    Fun("fraction-digits", classOf[MaxFractionDigitsValidation], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(INTEGER, ALLOWS_ZERO_OR_ONE)
    )

    Fun(ValidationFunctionNames.UploadMaxSize, classOf[UploadMaxSizeValidation], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(INTEGER, ALLOWS_ZERO_OR_ONE)
    )

    Fun(ValidationFunctionNames.UploadMediatypes, classOf[UploadMediatypesValidation], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, ALLOWS_ZERO_OR_ONE)
    )

    Fun("evaluate-avt", classOf[XXFormsEvaluateAVT], op = 0, min = 1, max = 10, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("form-urlencode", classOf[XXFormsFormURLEncode], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(NODE_TYPE, EXACTLY_ONE)
    )

    Fun("get-request-method", classOf[GetRequestMethodTryXFormsDocument], op = 0, 0, STRING, ALLOWS_ONE)
    Fun("get-request-context-path", classOf[GetRequestContextPathTryXFormsDocument], op = 0, 0, STRING, ALLOWS_ONE)

    Fun("get-request-path", classOf[GetRequestPathTryXFormsDocument], op = 0, 0, STRING, ALLOWS_ONE)

    Fun("get-request-header", classOf[GetRequestHeaderTryXFormsDocument], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("get-request-parameter", classOf[GetRequestParameterTryXFormsDocument], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun(ExcludedDatesValidation.PropertyName, classOf[ExcludedDatesValidation], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(DATE, ALLOWS_ZERO_OR_MORE)
    )
  }
}
