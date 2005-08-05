package org.orbeon.oxf.processor.sql.interpreters;

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.sql.SQLProcessor;
import org.orbeon.oxf.processor.sql.SQLProcessorInterpreterContext;
import org.orbeon.oxf.xml.dom4j.LocationData;

/**
 *
 */
public class DatasourceInterpreter extends SQLProcessor.InterpreterContentHandler {

    private StringBuffer datasourceName;

    public DatasourceInterpreter(SQLProcessorInterpreterContext interpreterContext) {
        super(interpreterContext, false);
    }

    public void characters(char[] chars, int start, int length) {
        if (datasourceName == null)
            datasourceName = new StringBuffer();
        datasourceName.append(chars, start, length);
    }

    public void end(String uri, String localname, String qName) {
        // Validate datasource element
        if (datasourceName == null)
            throw new ValidationException("Missing datasource name in datasource element", new LocationData(getDocumentLocator()));
        // Get the connection from the datasource and set in context
        try {
            getInterpreterContext().setConnection(getDocumentLocator(), datasourceName.toString());
        } catch (Exception e) {
            throw new ValidationException(e, new LocationData(getDocumentLocator()));
        }
    }
}
