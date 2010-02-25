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
package org.orbeon.oxf.processor.pipeline;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.pipeline.functions.RewriteResourceURI;
import org.orbeon.oxf.processor.pipeline.functions.RewriteServiceURI;
import org.orbeon.oxf.xforms.function.xxforms.XXFormsGetRequestAttribute;
import org.orbeon.oxf.xforms.function.xxforms.XXFormsPropertiesStartsWith;
import org.orbeon.oxf.xforms.function.xxforms.XXFormsProperty;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticProperty;
import org.orbeon.saxon.functions.*;
import org.orbeon.saxon.om.NamespaceConstant;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.ItemType;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.AtomicValue;
import org.orbeon.saxon.value.SequenceType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PipelineFunctionLibrary implements FunctionLibrary {

    private static final PipelineFunctionLibrary instance = new PipelineFunctionLibrary();

    public static PipelineFunctionLibrary instance() {
        return instance;
    }

    private static Map functionTable = new HashMap();

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

    // Functions are exposed statically below for access through XSLT

    public static AtomicValue property(String name) {
        return XXFormsProperty.property(name);
    }

    public static List propertiesStartsWith(String name) {
        return XXFormsPropertiesStartsWith.propertiesStartsWith(name);
    }

    public static String rewriteResourceURI(String uri, boolean absolute) {
        return RewriteResourceURI.rewriteResourceURI(uri, absolute);
    }

    public static String rewriteServiceURI(String uri, boolean absolute) {
        return RewriteServiceURI.rewriteServiceURI(uri, absolute);
    }

    static {
        StandardFunction.Entry e;

        // p:property() function
        e = register("{" + PipelineProcessor.PIPELINE_NAMESPACE_URI + "}property", XXFormsProperty.class, 0, 1, 1, Type.ANY_ATOMIC_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // p:rewrite-resource-uri() function
        e = register("{" + PipelineProcessor.PIPELINE_NAMESPACE_URI + "}rewrite-resource-uri", RewriteResourceURI.class, 0, 1, 2, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);

        // p:rewrite-service-uri() function
        e = register("{" + PipelineProcessor.PIPELINE_NAMESPACE_URI + "}rewrite-service-uri", RewriteServiceURI.class, 0, 1, 2, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);

        // p:get-request-attribute()
        e = register("{" + PipelineProcessor.PIPELINE_NAMESPACE_URI + "}get-request-attribute", XXFormsGetRequestAttribute.class, 0, 1, 2, Type.ITEM_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // Useful XSLT function
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
    }

    private StandardFunction.Entry getEntry(String uri, String local, int arity) {
        StandardFunction.Entry entry;
        if (uri.equals(NamespaceConstant.FN)) {
            entry = (StandardFunction.Entry) functionTable.get(local);
        } else if (uri.equals(PipelineProcessor.PIPELINE_NAMESPACE_URI)) {
            entry = (StandardFunction.Entry) functionTable.get("{" + uri + "}" + local);
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
            throw new OXFException("Failed to load function: " + err.getMessage(), err);
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
        // There is only one global function library, so we don't need a special hashCode() implementation
        return super.hashCode();
    }
}
