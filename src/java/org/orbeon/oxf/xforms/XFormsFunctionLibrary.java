/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.function.*;
import org.orbeon.oxf.xforms.function.Last;
import org.orbeon.oxf.xforms.function.exforms.EXFormsReadonly;
import org.orbeon.oxf.xforms.function.exforms.EXFormsRelevant;
import org.orbeon.oxf.xforms.function.exforms.EXFormsRequired;
import org.orbeon.oxf.xforms.function.exforms.EXFormsSort;
import org.orbeon.oxf.xforms.function.xxforms.*;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticProperty;
import org.orbeon.saxon.functions.*;
import org.orbeon.saxon.om.NamespaceConstant;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.ItemType;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.SequenceType;

import java.util.HashMap;
import java.util.Map;

public class XFormsFunctionLibrary implements FunctionLibrary {

    private static Map<String, StandardFunction.Entry> functionTable = new HashMap<String, StandardFunction.Entry>();

    private static StandardFunction.Entry register(String name,
                                                   Class implementationClass,
                                                   int opcode,
                                                   int minArguments,
                                                   int maxArguments,
                                                   ItemType itemType,
                                                   int cardinality) {
        StandardFunction.Entry e = new StandardFunction.Entry();
        e.name = name;
        e.implementationClass = implementationClass;
        e.opcode = opcode;
        e.minArguments = minArguments;
        e.maxArguments = maxArguments;
        e.itemType = itemType;
        e.cardinality = cardinality;
        e.argumentTypes = new SequenceType[maxArguments];

        functionTable.put(name, e);
        return e;
    }

    static {
        StandardFunction.Entry e;

        e = register("last", Last.class, 0, 0, 0, Type.INTEGER_TYPE, StaticProperty.EXACTLY_ONE);

        // 7.6 Boolean Functions

        e = register("boolean-from-string", BooleanFromString.class, 0, 1, 1, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        
        // 7.6.2 The is-card-number() Function
        e = register("is-card-number", IsCardNumber.class, 0, 1, 1, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // 7.7 Number Functions

        // avg(), min(), max() are implemented directly in XPath 2.0
        
        e = register("count-non-empty", CountNonEmpty.class, 0, 1, 1, Type.INTEGER_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.ANY_ATOMIC_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

        e = register("index", Index.class, 0, 1, 1, Type.INTEGER_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        e = register("power", Power.class, 0, 2, 2, Type.NUMBER_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NUMBER_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.NUMBER_TYPE, StaticProperty.EXACTLY_ONE);

        e = register("random", Random.class, 0, 0, 1, Type.NUMBER_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.BOOLEAN_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);

        // 7.8 String Functions

        // NOTE: Deprecated under this name. Use xxforms:if() instead
        e = register("xfif", If.class, 0, 3, 3, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 2, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        e = register("property", Property.class, 0, 1, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // The digest() function (XForms 1.1)
        e = register("digest", Digest.class, 0, 2, 3, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 2, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // The hmac() function (XForms 1.1)
        e = register("hmac", Hmac.class, 0, 3, 4, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 2, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 3, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // 7.9 Date and Time Functions

        e = register("local-date", LocalDate.class, 0, 0, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);

        e = register("local-dateTime", LocalDateTime.class, 0, 0, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);

        e = register("now", Now.class, 0, 0, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        
        e = register("days-from-date", DaysFromDate.class, 0, 1, 1, Type.INTEGER_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        e = register("days-to-date", DaysToDate.class, 0, 1, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.INTEGER_TYPE, StaticProperty.EXACTLY_ONE);

        e = register("seconds-from-dateTime", SecondsFromDateTime.class, 0, 1, 1, Type.INTEGER_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        e = register("seconds-to-dateTime", SecondsToDateTime.class, 0, 1, 1, Type.DATE_TIME_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, Type.NUMBER_TYPE, StaticProperty.EXACTLY_ONE);

        e = register("seconds", Seconds.class, 0, 1, 1, Type.DOUBLE_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        e = register("months", Months.class, 0, 1, 1, Type.INTEGER_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // 7.10 Node-set Functions
        
        // 7.10.1 The instance() Function
        e = register("instance", Instance.class, 0, 0, 1, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        register("context", Context.class, 0, 0, 0, Type.ITEM_TYPE, StaticProperty.EXACTLY_ONE);

        // 7.11 Object Functions

        e = register("choose", Choose.class, 0, 3, 3, Type.ITEM_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 2, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

        // The event() Function
        e = register("event", Event.class, 0, 1, 1, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // === Orbeon extension functions in the xxforms namespace

        // xxforms:event()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}event", XXFormsEvent.class, 0, 1, 1, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}case", XXFormsCase.class, 0, 1, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:get-request-header()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}get-request-header", XXFormsGetRequestHeader.class, 0, 1, 1, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:get-request-parameter()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}get-request-parameter", XXFormsGetRequestParameter.class, 0, 1, 1, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:get-session-attribute()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}get-session-attribute", XXFormsGetSessionAttribute.class, 0, 1, 2, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:set-session-attribute()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}set-session-attribute", XXFormsSetSessionAttribute.class, 0, 2, 2, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

        // xxforms:get-request-attribute()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}get-request-attribute", XXFormsGetRequestAttribute.class, 0, 1, 2, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:set-request-attribute()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}set-request-attribute", XXFormsSetRequestAttribute.class, 0, 2, 2, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

        // xxforms:get-remote-user()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}get-remote-user", XXFormsGetRemoteUser.class, 0, 0, 0, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO);

        // xxforms:is-user-in-role(xs:string) as xs:boolean
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}is-user-in-role", XXFormsIsUserInRole.class, 0, 1, 1, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:call-xpl
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}call-xpl", XXFormsCallXPL.class, 0, 4, 4, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        //StandardFunction.arg(e, 0, Type.ANY_URI_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 2, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 3, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

        // xxforms:evaluate
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}evaluate", Evaluate.class, Evaluate.EVALUATE, 1, 10, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:evaluate-avt
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}evaluate-avt", XXFormsEvaluateAVT.class, 0, 1, 10, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:serialize
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}serialize", Serialize.class, 0, 2, 2, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:repeat-current
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}repeat-current", XXFormsRepeatCurrent.class, 0, 0, 1, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:context
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}context", XXFormsContext.class, 0, 1, 1, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:repeat-nodeset
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}repeat-nodeset", XXFormsRepeatNodeset.class, 0, 0, 1, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:bind
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}bind", XXFormsBind.class, 0, 1, 1, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:bind-property
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}evaluate-bind-property", XXFormsEvaluateBindProperty.class, 0, 2, 2, Type.ANY_ATOMIC_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.ANY_ATOMIC_TYPE, StaticProperty.EXACTLY_ONE); // QName or String

        // xxforms:valid
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}valid", XXFormsValid.class, 0, 0, 2, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 1, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:type
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}type", XXFormsType.class, 0, 0, 1, Type.QNAME_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

        // xxforms:get-request-parameter()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}invalid-binds", XXFormsInvalidBinds.class, 0, 0, 1, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

        // xxforms:if
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}if", If.class, 0, 3, 3, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 2, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:element
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}element", XXFormsElement.class, 0, 1, 2, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.ANY_ATOMIC_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 1, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

        // xxforms:attribute
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}attribute", XXFormsAttribute.class, 0, 1, 2, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.ANY_ATOMIC_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 1, Type.ANY_ATOMIC_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:binding
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}binding", XXFormsBinding.class, 0, 1, 1, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:mutable
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}mutable-document", XXFormsMutableDocument.class, 0, 1, 1, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:form-urlencode
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}form-urlencode", XXFormsFormURLEncode.class, 0, 1, 1, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:component-context
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}component-context", XXFormsComponentContext.class, 0, 0, 0, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
    
        // xxforms:sort
        // TODO: Support XSLT 2.0 enhancements and multiple sort keys
//        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}sort", EXFormsSort.class, 0, 5, 5, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
//        StandardFunction.arg(e, 0, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
//        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.ALLOWS_ONE);
//        StandardFunction.arg(e, 2, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
//        StandardFunction.arg(e, 3, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
//        StandardFunction.arg(e, 4, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);

        // xxforms:instance
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}instance", XXFormsInstance.class, 0, 1, 1, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:index
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}index", XXFormsIndex.class, 0, 0, 1, Type.INTEGER_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);

        // xxforms:list-models
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}list-models", XXFormsListModels.class, 0, 0, 0, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

        // xxforms:list-instances
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}list-instances", XXFormsListInstances.class, 0, 1, 1, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:property
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}property", XXFormsProperty.class, 0, 1, 1, Type.ANY_ATOMIC_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:properties-start-with
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}properties-start-with", XXFormsPropertiesStartsWith.class, 0, 1, 1, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:decode-iso9075
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}decode-iso9075-14", XXFormsDecodeISO9075.class, 0, 1, 1, Type.STRING_TYPE, StaticProperty.ALLOWS_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:encode-iso9075
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}encode-iso9075-14", XXFormsEncodeISO9075.class, 0, 1, 1, Type.STRING_TYPE, StaticProperty.ALLOWS_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:doc-base64
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}doc-base64", XXFormsDocBase64.class, XXFormsDocBase64.DOC_BASE64, 1, 1, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);

        // xxforms:doc-base64-available
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}doc-base64-available", XXFormsDocBase64.class, XXFormsDocBase64.DOC_BASE64_AVAILABLE, 1, 1, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:extract-document
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}extract-document", XXFormsExtractDocument.class, 0, 1, 3, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 2, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:sort()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}sort", XXFormsSort.class, 0, 2, 5, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 1, Type.ITEM_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 2, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 3, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 4, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        
        // xxforms:itemset()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}itemset", XXFormsItemset.class, 0, 2, 2, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // === XSLT 2.0 function
        e = register("format-date", FormatDate.class, Type.DATE, 2, 5, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.DATE_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 2, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 3, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 4, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);

        e = register("format-dateTime", FormatDate.class, Type.DATE_TIME, 2, 5, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.DATE_TIME_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 2, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 3, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 4, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);

        e = register("format-number", FormatNumber2.class, 0, 2, 3, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NUMBER_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 2, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        e = register("format-time", FormatDate.class, Type.TIME, 2, 5, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.TIME_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 2, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 3, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 4, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);

        // === eXforms functions

        // exf:relevant()
        e = register("{" + XFormsConstants.EXFORMS_NAMESPACE_URI  + "}relevant", EXFormsRelevant.class, 0, 0, 1, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

        // exf:readonly()
        e = register("{" + XFormsConstants.EXFORMS_NAMESPACE_URI  + "}readonly", EXFormsReadonly.class, 0, 0, 1, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

        // exf:required()
        e = register("{" + XFormsConstants.EXFORMS_NAMESPACE_URI  + "}required", EXFormsRequired.class, 0, 0, 1, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

        // exf:sort()
        e = register("{" + XFormsConstants.EXFORMS_NAMESPACE_URI  + "}sort", EXFormsSort.class, 0, 2, 5, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 2, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 3, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 4, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
    }

    private StandardFunction.Entry getEntry(String uri, String local, int arity) {
        StandardFunction.Entry entry;
        if (uri.equals(NamespaceConstant.FN)) {
            entry = functionTable.get(local);
        } else if (uri.equals(XFormsConstants.XXFORMS_NAMESPACE_URI) || uri.equals(XFormsConstants.EXFORMS_NAMESPACE_URI)) {
            entry = functionTable.get("{" + uri + "}" + local);
        } else {
            return null;
        }

        if (entry == null || !(arity == -1 || arity >= entry.minArguments && arity <= entry.maxArguments)) {
            return null;
        }

        return entry;
    }

    public boolean isAvailable(int fingerprint, String uri, String local, int arity) {
        StandardFunction.Entry entry = getEntry(uri, local, arity);
        return entry != null;
    }

    public Expression bind(int nameCode, String uri, String local, Expression[] staticArgs) throws XPathException {
        StandardFunction.Entry entry = getEntry(uri, local, staticArgs.length);
        if (entry == null) {
            return null;
        }

        Class functionClass = entry.implementationClass;
        SystemFunction f;
        try {
            f = (SystemFunction) functionClass.newInstance();
        } catch (Exception err) {
            throw new OXFException("Failed to load XForms function: " + err.getMessage(), err);
        }
        f.setDetails(entry);
        f.setFunctionNameCode(nameCode);
        f.setArguments(staticArgs);
        return f;
    }

    public FunctionLibrary copy() {
        return this;
    }

    public int hashCode() {
        // There is only one global XForms function library, so we don't need a special hashCode() implementation
        return super.hashCode();
    }
}
