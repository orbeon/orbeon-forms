/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.transformer.xupdate;

import org.orbeon.oxf.transformer.xupdate.statement.Param;
import org.orbeon.oxf.transformer.xupdate.statement.Utils;

import javax.xml.transform.URIResolver;
import java.util.ArrayList;
import java.util.List;

public class Closure {

    private URIResolver uriResolver;
    private Object context;
    private VariableContextImpl variableContext;
    private DocumentContext documentContext;
    private Statement[] statements;

    public Closure(URIResolver uriRes, Object ctxt, Statement[] stmnts) {
        uriResolver = uriRes;
        context = ctxt;
        statements = stmnts;
    }

    public void setVariableContext(VariableContextImpl variableContext) {
        this.variableContext = variableContext;
    }

    public Object execute(List args) {
        int paramCount = 0;
        VariableContextImpl currentVariableContext = variableContext;
        List statementsToExecute = new ArrayList();
        for (int i = 0; i < statements.length; i++) {
            Statement statement = statements[i];
            if (statement instanceof Param) {
                Param param = (Param) statement;
                Object value = paramCount < args.size()
                        ? args.get(paramCount)
                        : param.execute(uriResolver, context, currentVariableContext, documentContext);
                currentVariableContext = new VariableContextImpl(currentVariableContext, param.getName(), value);
                paramCount++;
            } else {
                statementsToExecute.add(statement);
            }
        }
        return Utils.execute(uriResolver, context, currentVariableContext, documentContext, (Statement[]) statementsToExecute.toArray(new Statement[statementsToExecute.size()]));
    }
}
