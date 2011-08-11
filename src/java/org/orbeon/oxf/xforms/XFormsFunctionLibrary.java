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
import org.orbeon.oxf.xforms.function.Current;
import org.orbeon.oxf.xforms.function.Last;
import org.orbeon.oxf.xforms.function.exforms.EXFormsReadonly;
import org.orbeon.oxf.xforms.function.exforms.EXFormsRelevant;
import org.orbeon.oxf.xforms.function.exforms.EXFormsRequired;
import org.orbeon.oxf.xforms.function.exforms.EXFormsSort;
import org.orbeon.oxf.xforms.function.xxforms.*;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.StaticProperty;
import org.orbeon.saxon.functions.Aggregate;
import org.orbeon.saxon.functions.*;
import org.orbeon.saxon.om.NamespaceConstant;
import org.orbeon.saxon.om.StandardNames;
import org.orbeon.saxon.om.StructuredQName;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.BuiltInAtomicType;
import org.orbeon.saxon.type.ItemType;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.EmptySequence;
import org.orbeon.saxon.value.Int64Value;
import org.orbeon.saxon.value.SequenceType;
import org.orbeon.saxon.value.Value;

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
        e.resultIfEmpty = new Value[maxArguments];

        functionTable.put(name, e);
        return e;
    }

    static {
        StandardFunction.Entry e;

        // Saxon's last() function doesn't do what we need
        e = register("last", Last.class, 0, 0, 0, BuiltInAtomicType.INTEGER, StaticProperty.EXACTLY_ONE);

        // Forward these to our own implementation so we can handle PathMap
        e = register("count", org.orbeon.oxf.xforms.function.Aggregate.class, Aggregate.COUNT, 1, 1, BuiltInAtomicType.INTEGER, StaticProperty.EXACTLY_ONE);
            StandardFunction.arg(e, 0, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, Int64Value.ZERO);
        e = register("avg", org.orbeon.oxf.xforms.function.Aggregate.class, Aggregate.AVG, 1, 1, BuiltInAtomicType.ANY_ATOMIC, StaticProperty.ALLOWS_ZERO_OR_ONE);
            StandardFunction.arg(e, 0, BuiltInAtomicType.ANY_ATOMIC, StaticProperty.ALLOWS_ZERO_OR_MORE, EmptySequence.getInstance());
        e = register("sum", org.orbeon.oxf.xforms.function.Aggregate.class, Aggregate.SUM, 1, 2, BuiltInAtomicType.ANY_ATOMIC, StaticProperty.ALLOWS_ZERO_OR_ONE);
            StandardFunction.arg(e, 0, BuiltInAtomicType.ANY_ATOMIC, StaticProperty.ALLOWS_ZERO_OR_MORE, null);
            StandardFunction.arg(e, 1, BuiltInAtomicType.ANY_ATOMIC, StaticProperty.ALLOWS_ZERO_OR_ONE, null);

        // 7.6 Boolean Functions

        e = register("boolean-from-string", BooleanFromString.class, 0, 1, 1, BuiltInAtomicType.BOOLEAN, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        
        // 7.6.2 The is-card-number() Function
        e = register("is-card-number", IsCardNumber.class, 0, 1, 1, BuiltInAtomicType.BOOLEAN, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // 7.7 Number Functions

        // avg(), min(), max() are implemented directly in XPath 2.0
        
        e = register("count-non-empty", CountNonEmpty.class, 0, 1, 1, BuiltInAtomicType.INTEGER, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.ANY_ATOMIC, StaticProperty.ALLOWS_ZERO_OR_MORE, null);

        e = register("index", Index.class, 0, 1, 1, BuiltInAtomicType.INTEGER, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        e = register("power", Power.class, 0, 2, 2, BuiltInAtomicType.NUMERIC, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.NUMERIC, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.NUMERIC, StaticProperty.EXACTLY_ONE, null);

        e = register("random", Random.class, 0, 0, 1, BuiltInAtomicType.NUMERIC, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.BOOLEAN, StaticProperty.ALLOWS_ZERO_OR_ONE, null);

        // 7.8 String Functions

        // NOTE: Deprecated under this name. Use xxforms:if() instead
        e = register("xfif", If.class, 0, 3, 3, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.BOOLEAN, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 2, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        e = register("property", Property.class, 0, 1, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // The digest() function (XForms 1.1)
        e = register("digest", Digest.class, 0, 2, 3, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 2, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // The hmac() function (XForms 1.1)
        e = register("hmac", Hmac.class, 0, 3, 4, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 2, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 3, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // 7.9 Date and Time Functions

        e = register("local-date", LocalDate.class, 0, 0, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);

        e = register("local-dateTime", LocalDateTime.class, 0, 0, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);

        e = register("now", Now.class, 0, 0, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        
        e = register("days-from-date", DaysFromDate.class, 0, 1, 1, BuiltInAtomicType.INTEGER, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        e = register("days-to-date", DaysToDate.class, 0, 1, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.INTEGER, StaticProperty.EXACTLY_ONE, null);

        e = register("seconds-to-dateTime", SecondsToDateTime.class, 0, 1, 1, BuiltInAtomicType.DATE_TIME, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.NUMERIC, StaticProperty.EXACTLY_ONE, null);

        e = register("seconds", Seconds.class, 0, 1, 1, BuiltInAtomicType.DOUBLE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        e = register("months", Months.class, 0, 1, 1, BuiltInAtomicType.INTEGER, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // 7.10 Node-set Functions
        
        // 7.10.1 The instance() Function
        e = register("instance", Instance.class, 0, 0, 1, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // 7.10.2 The current() Function
        register("current", Current.class, 0, 0, 0, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        // 7.10.4 The context() Function
        register("context", Context.class, 0, 0, 0, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);

        // 7.11 Object Functions

        e = register("choose", Choose.class, 0, 3, 3, Type.ITEM_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.BOOLEAN, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, null);
        StandardFunction.arg(e, 2, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, null);

        // The event() Function
        e = register("event", Event.class, 0, 1, 1, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // === Orbeon extension functions in the xxforms namespace

        // xxforms:event()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}event", XXFormsEvent.class, 0, 1, 1, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}case", XXFormsCase.class, 0, 1, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:get-request-path()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}get-request-path", XXFormsGetRequestPath.class, 0, 0, 0, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ONE);

        // xxforms:get-request-header()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}get-request-header", XXFormsGetRequestHeader.class, 0, 1, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:get-request-parameter()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}get-request-parameter", XXFormsGetRequestParameter.class, 0, 1, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:get-session-attribute()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}get-session-attribute", XXFormsGetSessionAttribute.class, 0, 1, 2, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:set-session-attribute()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}set-session-attribute", XXFormsSetSessionAttribute.class, 0, 2, 2, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, null);

        // xxforms:get-request-attribute()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}get-request-attribute", XXFormsGetRequestAttribute.class, 0, 1, 2, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:set-request-attribute()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}set-request-attribute", XXFormsSetRequestAttribute.class, 0, 2, 2, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, null);

        // xxforms:get-remote-user()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}get-remote-user", XXFormsGetRemoteUser.class, 0, 0, 0, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE);

        // xxforms:is-user-in-role(xs:string) as xs:boolean
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}is-user-in-role", XXFormsIsUserInRole.class, 0, 1, 1, BuiltInAtomicType.BOOLEAN, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:call-xpl
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}call-xpl", XXFormsCallXPL.class, 0, 4, 4, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        //StandardFunction.arg(e, 0, Type.ANY_URI_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_MORE, null);
        StandardFunction.arg(e, 2, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, null);
        StandardFunction.arg(e, 3, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_MORE, null);

        // xxforms:evaluate
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}evaluate", Evaluate.class, Evaluate.EVALUATE, 1, 10, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:evaluate-avt
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}evaluate-avt", XXFormsEvaluateAVT.class, 0, 1, 10, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:serialize
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}serialize", Serialize.class, 0, 2, 2, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 1, Type.ITEM_TYPE, StaticProperty.EXACTLY_ONE, null);

        // xxforms:repeat-current
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}repeat-current", XXFormsRepeatCurrent.class, 0, 0, 1, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:repeat-position
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}repeat-position", XXFormsRepeatPosition.class, 0, 0, 1, BuiltInAtomicType.INTEGER, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:context
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}context", XXFormsContext.class, 0, 1, 1, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:repeat-nodeset
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}repeat-nodeset", XXFormsRepeatNodeset.class, 0, 0, 1, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:bind
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}bind", XXFormsBind.class, 0, 1, 1, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:bind-property
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}evaluate-bind-property", XXFormsEvaluateBindProperty.class, 0, 2, 2, BuiltInAtomicType.ANY_ATOMIC, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.ANY_ATOMIC, StaticProperty.EXACTLY_ONE, null); // QName or String

        // xxforms:valid
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}valid", XXFormsValid.class, 0, 0, 2, BuiltInAtomicType.BOOLEAN, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.BOOLEAN, StaticProperty.EXACTLY_ONE, null);

        // xxforms:type
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}type", XXFormsType.class, 0, 0, 1, BuiltInAtomicType.QNAME, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, null);

        // xxforms:get-request-parameter()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}invalid-binds", XXFormsInvalidBinds.class, 0, 0, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, null);

        // xxforms:if
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}if", If.class, 0, 3, 3, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.BOOLEAN, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 2, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:element
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}element", XXFormsElement.class, 0, 1, 2, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.ANY_ATOMIC, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 1, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, null);

        // xxforms:attribute
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}attribute", XXFormsAttribute.class, 0, 1, 2, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.ANY_ATOMIC, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.ANY_ATOMIC, StaticProperty.EXACTLY_ONE, null);

        // xxforms:binding
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}binding", XXFormsBinding.class, 0, 1, 1, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:mutable
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}mutable-document", XXFormsMutableDocument.class, 0, 1, 1, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE, null);

        // xxforms:form-urlencode
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}form-urlencode", XXFormsFormURLEncode.class, 0, 1, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE, null);

        // xxforms:component-context
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}component-context", XXFormsComponentContext.class, 0, 0, 0, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
    
        // xxforms:sort
        // TODO: Support XSLT 2.0 enhancements and multiple sort keys
//        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}sort", EXFormsSort.class, 0, 5, 5, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
//        StandardFunction.arg(e, 0, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
//        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ONE);
//        StandardFunction.arg(e, 2, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE);
//        StandardFunction.arg(e, 3, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE);
//        StandardFunction.arg(e, 4, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE);

        // xxforms:instance
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}instance", XXFormsInstance.class, 0, 1, 2, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.BOOLEAN, StaticProperty.EXACTLY_ONE, null);

        // xxforms:index
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}index", XXFormsIndex.class, 0, 0, 1, BuiltInAtomicType.INTEGER, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);

        // xxforms:list-models
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}list-models", XXFormsListModels.class, 0, 0, 0, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_MORE);

        // xxforms:list-instances
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}list-instances", XXFormsListInstances.class, 0, 1, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:list-variables
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}list-variables", XXFormsListVariables.class, 0, 1, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:get-variable
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}get-variable", XXFormsGetVariable.class, 0, 2, 2, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:property
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}property", XXFormsProperty.class, 0, 1, 1, BuiltInAtomicType.ANY_ATOMIC, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:properties-start-with
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}properties-start-with", XXFormsPropertiesStartsWith.class, 0, 1, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:decode-iso9075
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}decode-iso9075-14", XXFormsDecodeISO9075.class, 0, 1, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:encode-iso9075
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}encode-iso9075-14", XXFormsEncodeISO9075.class, 0, 1, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:doc-base64
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}doc-base64", XXFormsDocBase64.class, XXFormsDocBase64.DOC_BASE64, 1, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);

        // xxforms:doc-base64-available
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}doc-base64-available", XXFormsDocBase64.class, XXFormsDocBase64.DOC_BASE64_AVAILABLE, 1, 1, BuiltInAtomicType.BOOLEAN, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:extract-document
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}extract-document", XXFormsExtractDocument.class, 0, 1, 3, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 2, BuiltInAtomicType.BOOLEAN, StaticProperty.EXACTLY_ONE, null);

        // xxforms:sort()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}sort", XXFormsSort.class, 0, 2, 5, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, null);
        StandardFunction.arg(e, 1, Type.ITEM_TYPE, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 2, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 3, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 4, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        
        // xxforms:itemset()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}itemset", XXFormsItemset.class, 0, 2, 3, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 2, BuiltInAtomicType.BOOLEAN, StaticProperty.EXACTLY_ONE, null);

        // xxforms:format-message()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}format-message", XXFormsFormatMessage.class, 0, 2, 2, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, null);

        // xxforms:lang()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}lang", XXFormsLang.class, 0, 0, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:pending-uploads()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}pending-uploads", XXFormsPendingUploads.class, 0, 0, 0, BuiltInAtomicType.INTEGER, StaticProperty.EXACTLY_ONE);

        // xxforms:document-id()
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}document-id", XXFormsDocumentId.class, 0, 0, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);

        // xxforms:document
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}create-document", XXFormsCreateDocument.class, 0, 0, 0, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:label, xxforms:help, xxforms:hint, xxforms:alert
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}label", XXFormsLHHA.class, 0, 1, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}help", XXFormsLHHA.class, 1, 1, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}hint", XXFormsLHHA.class, 2, 1, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}alert", XXFormsLHHA.class, 3, 1, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:effective-id
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}effective-id", XXFormsEffectiveId.class, 0, 0, 1, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xxforms:control-element
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}control-element", XXFormsControlElement.class, 0, 0, 1, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, null);

        // === Functions in the xforms namespace

        // xforms:if()
        e = register("{" + XFormsConstants.XFORMS_NAMESPACE_URI  + "}if", If.class, 0, 3, 3, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.BOOLEAN, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 2, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // xforms:seconds-from-dateTime(), which is incompatible with the XPath 2.0 version
        e = register("{" + XFormsConstants.XFORMS_NAMESPACE_URI  + "}seconds-from-dateTime", SecondsFromDateTime.class, 0, 1, 1, BuiltInAtomicType.DECIMAL, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        // === XSLT 2.0 function
        e = register("format-date", FormatDate.class, StandardNames.XS_DATE, 2, 5, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.DATE, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 2, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 3, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 4, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);

        e = register("format-dateTime", FormatDate.class, StandardNames.XS_DATE_TIME, 2, 5, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.DATE_TIME, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 2, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 3, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 4, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);

        e = register("format-number", FormatNumber.class, 0, 2, 3, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.NUMERIC, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 2, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);

        e = register("format-time", FormatDate.class, StandardNames.XS_TIME, 2, 5, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, BuiltInAtomicType.TIME, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 2, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 3, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 4, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);

        // === eXforms functions

        // exf:relevant()
        e = register("{" + XFormsConstants.EXFORMS_NAMESPACE_URI  + "}relevant", EXFormsRelevant.class, 0, 0, 1, BuiltInAtomicType.BOOLEAN, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, null);

        // exf:readonly()
        e = register("{" + XFormsConstants.EXFORMS_NAMESPACE_URI  + "}readonly", EXFormsReadonly.class, 0, 0, 1, BuiltInAtomicType.BOOLEAN, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, null);

        // exf:required()
        e = register("{" + XFormsConstants.EXFORMS_NAMESPACE_URI  + "}required", EXFormsRequired.class, 0, 0, 1, BuiltInAtomicType.BOOLEAN, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, null);

        // exf:sort()
        e = register("{" + XFormsConstants.EXFORMS_NAMESPACE_URI  + "}sort", EXFormsSort.class, 0, 2, 5, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE, null);
        StandardFunction.arg(e, 1, BuiltInAtomicType.STRING, StaticProperty.EXACTLY_ONE, null);
        StandardFunction.arg(e, 2, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 3, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
        StandardFunction.arg(e, 4, BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_ONE, null);
    }

    private StandardFunction.Entry getEntry(String uri, String local, int arity) {
        StandardFunction.Entry entry;
        if (uri.equals(NamespaceConstant.FN)) {
            entry = functionTable.get(local);
        } else if (uri.equals(XFormsConstants.XXFORMS_NAMESPACE_URI) || uri.equals(XFormsConstants.EXFORMS_NAMESPACE_URI) || uri.equals(XFormsConstants.XFORMS_NAMESPACE_URI)) {
            entry = functionTable.get("{" + uri + "}" + local);
        } else {
            return null;
        }

        if (entry == null || !(arity == -1 || arity >= entry.minArguments && arity <= entry.maxArguments)) {
            return null;
        }

        return entry;
    }

    public boolean isAvailable(StructuredQName functionName, int arity) {
        StandardFunction.Entry entry = getEntry(functionName.getNamespaceURI(), functionName.getLocalName(), arity);
        return entry != null;
    }

    public Expression bind(StructuredQName functionName, Expression[] staticArgs, StaticContext env) throws XPathException {
        StandardFunction.Entry entry = getEntry(functionName.getNamespaceURI(), functionName.getLocalName(), staticArgs.length);
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
        f.setFunctionName(functionName);
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
