/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.function.*;
import org.orbeon.oxf.xforms.function.Last;
import org.orbeon.oxf.xforms.function.exforms.EXFormsReadonly;
import org.orbeon.oxf.xforms.function.exforms.EXFormsRelevant;
import org.orbeon.oxf.xforms.function.exforms.EXFormsRequired;
import org.orbeon.oxf.xforms.function.xxforms.XXFormsCallXPL;
import org.orbeon.oxf.xforms.function.xxforms.XXFormsValid;
import org.orbeon.oxf.xforms.function.xxforms.XXFormsRepeatCurrent;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticProperty;
import org.orbeon.saxon.functions.*;
import org.orbeon.saxon.om.NamespaceConstant;
import org.orbeon.saxon.type.ItemType;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.SequenceType;
import org.orbeon.saxon.xpath.XPathException;

import java.util.HashMap;
import java.util.Map;

public class XFormsFunctionLibrary implements FunctionLibrary {


    private static Map functionTable = new HashMap();

    private XFormsModel xFormsModel;
    private XFormsControls xFormsControls;

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

        // 7.7 Boolean Functions
        e = register("boolean-from-string", BooleanFromString.class, 0, 1, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        e = register("xfif", If.class, 0, 3, 3, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 2, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // 7.8 Number Funtions (avg(), min(), max() are implemented in XPath 2.0)
        e = register("count-non-empty", CountNonEmpty.class, 0, 1, 1, Type.INTEGER_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.ANY_ATOMIC_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

        e = register("index", Index.class, 0, 1, 1, Type.INTEGER_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // 7.9 String Functions
        e = register("property", Property.class, 0, 1, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // 7.10 Date and Time Functions

        // Masquerade Saxon's current-datetime()
        e = register("now", Now.class, 0, 0, 1, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);

        e = register("days-from-date", DaysFromDate.class, 0, 1, 1, Type.INTEGER_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        e = register("seconds-from-dateTime", SecondsFromDateTime.class, 0, 1, 1, Type.INTEGER_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        e = register("seconds", Seconds.class, 0, 1, 1, Type.DOUBLE_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        e = register("months", Months.class, 0, 1, 1, Type.INTEGER_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // 7.11.1 The instance() Function
        e = register("instance", Instance.class, 0, 1, 1, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // OPS XXForms functions
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

        // xxforms:repeat-current
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI + "}repeat-current", XXFormsRepeatCurrent.class, 0, 0, 1, Type.NODE_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);

        // xxforms:valid
        e = register("{" + XFormsConstants.XXFORMS_NAMESPACE_URI  + "}valid", XXFormsValid.class, 0, 1, 1, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

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

        // eXForms functions
        e = register("{" + XFormsConstants.EXFORMS_NAMESPACE_URI  + "}relevant", EXFormsRelevant.class, 0, 1, 1, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

        e = register("{" + XFormsConstants.EXFORMS_NAMESPACE_URI  + "}readonly", EXFormsReadonly.class, 0, 1, 1, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

        e = register("{" + XFormsConstants.EXFORMS_NAMESPACE_URI  + "}required", EXFormsRequired.class, 0, 1, 1, Type.BOOLEAN_TYPE, StaticProperty.EXACTLY_ONE);
        StandardFunction.arg(e, 0, Type.NODE_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);
    }

    /**
     * This constructor is used when the function context includes an XForms model.
     *
     * @param xFormsModel
     */
    public XFormsFunctionLibrary(XFormsModel xFormsModel, XFormsControls xFormsControls) {
        this.xFormsModel = xFormsModel;
        this.xFormsControls = xFormsControls;
    }

    /**
     * This constructor is used when the function context includes XForms controls only.
     *
     * @param xFormsControls
     */
    public XFormsFunctionLibrary(XFormsControls xFormsControls) {
        this.xFormsControls = xFormsControls;
    }

    private StandardFunction.Entry getEntry(String uri, String local, int arity) {
        StandardFunction.Entry entry;
        if (uri.equals(NamespaceConstant.FN)) {
            entry = (StandardFunction.Entry) functionTable.get(local);
        } else if (uri.equals(XFormsConstants.XXFORMS_NAMESPACE_URI) || uri.equals(XFormsConstants.EXFORMS_NAMESPACE_URI)) {
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
            throw new OXFException("Failed to load XForms function: " + err.getMessage(), err);
        }
        // Set function context if it's one of ours
        if (f instanceof XFormsFunction) {
            if (xFormsControls != null)
                ((XFormsFunction) f).setXFormsControls(xFormsControls);
            if (xFormsModel != null)
                ((XFormsFunction) f).setXFormsModel(xFormsModel);
        }
        f.setDetails(entry);
        f.setFunctionNameCode(nameCode);
        f.setArguments(staticArgs);
        return f;
    }
}
