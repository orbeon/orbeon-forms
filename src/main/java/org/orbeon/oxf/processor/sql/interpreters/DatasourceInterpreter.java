package org.orbeon.oxf.processor.sql.interpreters;

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.sql.SQLProcessor;
import org.orbeon.oxf.processor.sql.SQLProcessorInterpreterContext;
import org.orbeon.oxf.xml.dom.XmlLocationData;

/**
 *
 */
public class DatasourceInterpreter extends SQLProcessor.InterpreterContentHandler {

    private StringBuilder datasourceName;

    public DatasourceInterpreter(SQLProcessorInterpreterContext interpreterContext) {
        super(interpreterContext, false);
    }

    public void characters(char[] chars, int start, int length) {
        if (datasourceName == null)
            datasourceName = new StringBuilder();
        datasourceName.append(chars, start, length);
    }

    public void end(String uri, String localname, String qName) {
        // Validate datasource element
        if (datasourceName == null)
            throw new ValidationException("Missing datasource name in datasource element", XmlLocationData.apply(getDocumentLocator()));
        // Get the connection from the datasource and set in context
        try {
            getInterpreterContext().setConnection(getDocumentLocator(), datasourceName.toString());
        } catch (Exception e) {
            throw new ValidationException(e, XmlLocationData.apply(getDocumentLocator()));
        }
    }
}
