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
package org.orbeon.oxf.processor.pdf;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.StaticProperty;
import org.orbeon.saxon.functions.*;
import org.orbeon.saxon.om.NamespaceConstant;
import org.orbeon.saxon.om.StandardNames;
import org.orbeon.saxon.om.StructuredQName;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.BuiltInAtomicType;
import org.orbeon.saxon.type.ItemType;
import org.orbeon.saxon.value.SequenceType;
import org.orbeon.saxon.value.Value;

import java.util.HashMap;
import java.util.Map;

public class PDFFunctionLibrary implements FunctionLibrary {

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
    }

    private StandardFunction.Entry getEntry(String uri, String local, int arity) {
        StandardFunction.Entry entry;
        if (uri.equals(NamespaceConstant.FN)) {
            entry = functionTable.get(local);
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
}
