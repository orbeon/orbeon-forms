package org.orbeon.oxf.xforms;

import org.dom4j.Element;
import org.orbeon.saxon.om.ValueRepresentation;
import org.orbeon.saxon.value.EmptySequence;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.common.ValidationException;

import java.util.List;

/**
 * Represents an exforms:variable / xxforms:variable element.
 */
public class Variable {

    private XFormsContainingDocument containingDocument;
    private XFormsContextStack contextStack;
    private Element element;

    private String variableName;
    private String selectAttribute;

    private boolean evaluated;
    private ValueRepresentation variableValue;

    public Variable(XFormsContainingDocument containingDocument, XFormsContextStack contextStack, Element element) {
        this.containingDocument = containingDocument;
        this.contextStack = contextStack;
        this.element = element;

        this.variableName = element.attributeValue("name");
        if (variableName == null)
            throw new ValidationException("xforms:variable or exforms:variable element must have a \"name\" attribute", getLocationData());

        this.selectAttribute = element.attributeValue("select");
        if (selectAttribute == null)
            throw new ValidationException("xforms:variable or exforms:variable element must have a \"select\" attribute", getLocationData());
    }

    private void evaluate(PipelineContext pipelineContext, boolean useCache) {

        final XFormsContextStack.BindingContext bindingContext = contextStack.getCurrentBindingContext();

        final List currentNodeset = bindingContext.getNodeset();
        if (currentNodeset != null && currentNodeset.size() > 0) {
            variableValue = XPathCache.evaluateAsVariable(pipelineContext,
                    currentNodeset, bindingContext.getPosition(),
                    selectAttribute, containingDocument.getNamespaceMappings(element), bindingContext.getInScopeVariables(useCache),
                    XFormsContainingDocument.getFunctionLibrary(), contextStack.getFunctionContext(), null, getLocationData());

        } else {
            variableValue = EmptySequence.getInstance();
        }
    }

    public String getVariableName() {
        return variableName;
    }

    public ValueRepresentation getVariableValue(PipelineContext pipelineContext, boolean useCache) {
        if (!evaluated) {
            evaluated = true;
            evaluate(pipelineContext, useCache);
        }
        return variableValue;
    }

    public LocationData getLocationData() {
        return (element != null) ? (LocationData) element.getData() : null;
    }
}
