package org.orbeon.faces11;

import com.sun.faces.el.VariableResolverImpl;

import javax.faces.context.FacesContext;
import javax.faces.el.EvaluationException;

import org.orbeon.oxf.processor.xforms.input.Instance;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.xml.XMLUtils;

/**
 *
 */
public class VariableResolver  extends VariableResolverImpl {

    public Object resolveVariable(FacesContext facesContext, String s) throws EvaluationException {

        System.out.println("resolveVariable: " + s);

        if (s.equals("Instance")) {
            Instance instance = Instance.createInstanceFromContext(StaticExternalContext.getStaticContext().getPipelineContext());

            if (instance != null) {
                System.out.println(XMLUtils.domToString(instance.getDocument()));
                return new PropertyResolver.XMLDocument(instance.getDocument());
            } else {
                return null;
            }
        }
        return super.resolveVariable(facesContext, s);
    }
}
