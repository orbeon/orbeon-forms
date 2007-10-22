package org.orbeon.oxf.processor;

import org.dom4j.Document;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.pipeline.api.PipelineContext;

/**
 * Datasource represents a simple datasource configuration.
 *
 * Used by the SQL and XML:DB processors.
 */
public class Datasource {

    public static final String DRIVER_CLASS_NAME = "driver-class-name";
    public static final String URI_PROPERTY = "uri";
    public static final String USERNAME_PROPERTY = "username";
    public static final String PASSWORD_PROPERTY = "password";

    private String driverClassName;
    private String uri;
    private String username;
    private String password;

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Datasource() {

    }

    public Datasource(String driverClassName, String uri, String username, String password) {
        this.driverClassName = driverClassName;
        this.uri = uri;
        this.username = username;
        this.password = password;
    }

    private Datasource(ProcessorImpl processorImpl, Document datasourceDocument) {

        // Try local configuration first
        String driverClassName = XPathUtils.selectStringValueNormalize(datasourceDocument, "/*/" + DRIVER_CLASS_NAME);
        String url = XPathUtils.selectStringValueNormalize(datasourceDocument, "/*/" + URI_PROPERTY);
        String username = XPathUtils.selectStringValueNormalize(datasourceDocument, "/*/" + USERNAME_PROPERTY);
        String password = XPathUtils.selectStringValueNormalize(datasourceDocument, "/*/" + PASSWORD_PROPERTY);

        // Override with properties if needed
        setDriverClassName(driverClassName != null ? driverClassName : processorImpl.getPropertySet().getString(DRIVER_CLASS_NAME));
        setUri(url != null ? url : processorImpl.getPropertySet().getString(URI_PROPERTY));
        setUsername(username != null ? username : processorImpl.getPropertySet().getString(USERNAME_PROPERTY));
        setPassword(password != null ? password : processorImpl.getPropertySet().getString(PASSWORD_PROPERTY));
    }

    /**
     * Read a processor input into a datasource.
     *
     * @param pipelineContext  current pipeline context
     * @param processorImpl    instance of ProcessorImpl
     * @param datasourceInput  input referring to the datasource definition
     * @return Datasource object
     */
    public static Datasource getDatasource(PipelineContext pipelineContext, final ProcessorImpl processorImpl, final ProcessorInput datasourceInput) {
        return (Datasource) processorImpl.readCacheInputAsObject(pipelineContext, datasourceInput, new CacheableInputReader() {
            public Object read(PipelineContext context, ProcessorInput input) {
                return new Datasource(processorImpl, processorImpl.readInputAsDOM4J(context, datasourceInput));
            }
        });
    }

    public String toString() {
        return "[" + driverClassName + "|" + uri + "|" + username + "]";
    }
}
