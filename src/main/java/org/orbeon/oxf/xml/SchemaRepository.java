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
package org.orbeon.oxf.xml;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.controller.PageFlowControllerProcessor;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.XQuery.XQueryProcessor;
import org.orbeon.oxf.processor.converter.QNameConverter;
import org.orbeon.oxf.processor.converter.XMLConverter;
import org.orbeon.oxf.processor.execute.ExecuteProcessor;
import org.orbeon.oxf.processor.file.FileProcessor;
import org.orbeon.oxf.processor.generator.*;
import org.orbeon.oxf.processor.pdf.PDFTemplateProcessor;
import org.orbeon.oxf.processor.pipeline.AggregatorProcessor;
import org.orbeon.oxf.processor.pipeline.PipelineProcessor;
import org.orbeon.oxf.processor.scope.ScopeProcessorBase;
import org.orbeon.oxf.processor.serializer.CachedSerializer;
import org.orbeon.oxf.processor.serializer.HttpSerializer;
import org.orbeon.oxf.processor.sql.SQLProcessor;
import org.orbeon.oxf.processor.transformer.TraxTransformer;
import org.orbeon.oxf.processor.transformer.xslt.XSLT1Transformer;
import org.orbeon.oxf.processor.transformer.xslt.XSLTTransformer;
import org.orbeon.oxf.processor.validation.MSVValidationProcessor;
import org.orbeon.oxf.properties.Properties;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Global repository which maps schema URIs to file URIs.
 */
public class SchemaRepository {

    private static final String SCHEMA_PREFIX = "oxf.schemas.";

    private static final String BASE_PATH = "/org/orbeon/oxf/";
    private static final String GLOBAL_SCHEMA_PATH = BASE_PATH + "xml/"; // NOTE: now trying to put schemas long with respective classes instead
    private static final String PROCESSORS_SCHEMA_PATH = BASE_PATH + "processor/";
    private static final String PORTLET_PROCESSORS_SCHEMA_PATH = BASE_PATH + "portlet/processor/";

    private static final Map<String, String> SCHEMAS = new HashMap<String, String>();

    private static SchemaRepository instance;

    static {
        SCHEMAS.put(Properties.PROPERTIES_SCHEMA_URI, BASE_PATH + "properties/properties.rng");

        SCHEMAS.put(PipelineProcessor.PIPELINE_NAMESPACE_URI, "schemas/pipeline.rng");
        SCHEMAS.put(AggregatorProcessor.AGGREGATOR_NAMESPACE_URI, "schemas/aggregator.rng");
        SCHEMAS.put(DelegationProcessor.DELEGATION_NAMESPACE_URI, "schemas/delegation.rng");
        SCHEMAS.put(JavaProcessor.JAVA_CONFIG_NAMESPACE_URI, "schemas/java.rng");
        SCHEMAS.put(URLGenerator.URL_NAMESPACE_URI, "schemas/url-generator-config.rng");

        SCHEMAS.put(RedirectProcessor.REDIRECT_SCHEMA_URI, "schemas/redirect.rng");
        SCHEMAS.put(RequestGenerator.REQUEST_CONFIG_NAMESPACE_URI, "schemas/request-config.rng");
        SCHEMAS.put(ImageServer.IMAGE_SERVER_CONFIG_NAMESPACE_URI, "schemas/image-server-config.rng");
        SCHEMAS.put(ImageServer.IMAGE_SERVER_IMAGE_NAMESPACE_URI, "schemas/image-server-image.rng");
        SCHEMAS.put(RequestSecurityGenerator.REQUEST_SECURITY_NAMESPACE_URI, "schemas/request-security.rng");
        SCHEMAS.put(SignatureVerifierProcessor.SIGNATURE_DATA_URI, "schemas/signature.rng");
        SCHEMAS.put(SignatureVerifierProcessor.SIGNATURE_PUBLIC_KEY_URI, "schemas/public-key.rng");
        SCHEMAS.put(XMLProcessorRegistry.PROCESSOR_REGISTRY_CONFIG_NAMESPACE_URI, "schemas/processor-registry.rng");

        // Serializers schemas
        SCHEMAS.put(CachedSerializer.SERIALIZER_CONFIG_NAMESPACE_URI, PROCESSORS_SCHEMA_PATH + "serializer/legacy-serializer-config.rng");
        SCHEMAS.put(HttpSerializer.HTTP_SERIALIZER_CONFIG_NAMESPACE_URI, PROCESSORS_SCHEMA_PATH + "serializer/http-serializer-config.rng");
        SCHEMAS.put("http://orbeon.org/oxf/xml/file-serializer-config", "schemas/file-serializer-config.rng");

        // Converter schemas
        SCHEMAS.put(XMLConverter.STANDARD_TEXT_CONVERTER_CONFIG_NAMESPACE_URI, PROCESSORS_SCHEMA_PATH + "converter/standard-text-converter-config.rng");
        SCHEMAS.put(QNameConverter.QNAME_CONVERTER_CONFIG_NAMESPACE_URI, PROCESSORS_SCHEMA_PATH + "converter/qname-converter-config.rng");

        SCHEMAS.put(ScopeProcessorBase.ScopeConfigNamespaceUri(), PROCESSORS_SCHEMA_PATH + "scope/scope-config.rng");

        SCHEMAS.put(EmailProcessor.ConfigNamespaceURI(), "schemas/email.rng");
        SCHEMAS.put(BeanGenerator.BEAN_CONFIG_NAMESPACE_URI, "schemas/bean-generator-config.rng");
        SCHEMAS.put(ResourceServer.RESOURCE_SERVER_NAMESPACE_URI, "schemas/resource-server.rng");
        SCHEMAS.put(LDAPProcessor.LDAP_CONFIG_NAMESPACE_URI, "schemas/ldap-config.rng");
        SCHEMAS.put(LDAPProcessor.LDAP_FILTER_NAMESPACE_URI, "schemas/ldap-filter.rng");
        SCHEMAS.put(SchedulerProcessor.SCHEDULER_CONFIG_NAMESPACE_URI, "schemas/scheduler-config.rng");

        // Portlet schemas
        SCHEMAS.put("http://orbeon.org/oxf/xml/portlet-preferences-serializer-data", PORTLET_PROCESSORS_SCHEMA_PATH + "portlet-preferences-serializer-data.rng");

        // XSLT schemas
        SCHEMAS.put(XSLTTransformer.XSLT_TRANSFORMER_CONFIG_NAMESPACE_URI, "schemas/xslt-transformer-config.rng");
        SCHEMAS.put(XSLTTransformer.XSLT_PREFERENCES_CONFIG_NAMESPACE_URI, "schemas/attributes-config.rng");
        SCHEMAS.put(TraxTransformer.TRAX_TRANSFORMER_CONFIG_NAMESPACE_URI, "schemas/trax-transformer-config.rng");

        SCHEMAS.put(PageFlowControllerProcessor.ControllerNamespaceURI(), "schemas/page-flow-controller.rng");
        SCHEMAS.put(MSVValidationProcessor.ORBEON_ERROR_NS, "schemas/validation-config.rng");
        SCHEMAS.put(DirectoryScannerProcessor.DIRECTORY_GENERATOR_NAMESPACE_URI, "schemas/directory-generator-config.rng");

        // SQL processor schemas
        SCHEMAS.put(SQLProcessor.SQL_NAMESPACE_URI, PROCESSORS_SCHEMA_PATH + "sql/sql-processor-config.rng");
        SCHEMAS.put(SQLProcessor.SQL_DATASOURCE_URI, PROCESSORS_SCHEMA_PATH + "sql/sql-processor-datasource.rng");

        // XQuery Generator schema
        SCHEMAS.put(XQueryProcessor.XQUERY_NAMESPACE_URI, PROCESSORS_SCHEMA_PATH + "XQuery/xquery-processor.rng");

        // Test processor schema
        // 2016-06-09: Not good that we have to hardcode this, but the processor is now in a different module. Processors
        // should be able to add themselves to the repository somehow.
        SCHEMAS.put("http://www.orbeon.org/oxf/xml/schemas/test-processor", PROCESSORS_SCHEMA_PATH + "test/test-processor-config.rng");

        SCHEMAS.put(PDFTemplateProcessor.PDFTemplateModelNamespaceURI(), PROCESSORS_SCHEMA_PATH + "pdf/pdf-template-model.rng");

        SCHEMAS.put("http://relaxng.org/ns/structure/1.0", "schemas/relaxng.rng");
        SCHEMAS.put("http://www.w3.org/2001/xml-events", "schemas/XML-Events-Schema.xsd");
        SCHEMAS.put("http://www.w3.org/2001/XMLSchema", "schemas/XMLSchema.xsd");
        SCHEMAS.put("http://www.w3.org/XML/1998/namespace", "schemas/xml.xsd");

        // XSLT schemas
        SCHEMAS.put(XSLT1Transformer.XSLT_1_0_PSEUDO_URI, "schemas/xslt-1_0.rng");// Schema for 1.0 only
        SCHEMAS.put(XSLTTransformer.XSLT_URI, "schemas/xslt-2_0.xsd");// Schema for 1.0 and 2.0, should be less restrictive

        // Other schemas
        SCHEMAS.put(FileProcessor.FILE_PROCESSOR_CONFIG_NAMESPACE_URI, PROCESSORS_SCHEMA_PATH + "file/file-processor-config.rng");
        SCHEMAS.put(ExecuteProcessor.EXECUTE_PROCESSOR_CONFIG_NAMESPACE_URI, PROCESSORS_SCHEMA_PATH + "execute/execute-processor-config.rng");
    }

    private SchemaRepository() {
        // Don't allow creation from outside
    }

    /**
     * Return the global instance of SchemaRepository.
     *
     * @return SchemaRepository
     */
    public static SchemaRepository instance() {
        if (instance == null)
            instance = new SchemaRepository();
        return instance;
    }

    /**
     * Get a systemId by looking in the properties, then in the builtin Orbeon Forms schemas. This is used for
     * user-defined validation.
     */
    public String getSchemaLocation(String publicId) {
        // First look in the properties
        final Set keys = Properties.instance().keySet();
        if (keys != null) {
            for (Iterator i = keys.iterator(); i.hasNext();) {
                final String currentKey = (String) i.next();
                if (currentKey.equals(SCHEMA_PREFIX + publicId)) {
                    return Properties.instance().getPropertySet().getString(currentKey);
                }
            }
        }

        // Not defined in properties: try predefined Orbeon Forms schemas
        final String schemaFile = SCHEMAS.get(publicId);
        if (schemaFile != null) {
            if (schemaFile.startsWith("/"))
                return "oxf:" + schemaFile;
            else
                return "oxf:" + GLOBAL_SCHEMA_PATH + schemaFile;
        }

        throw new OXFException("Can't load schema for URI: " + publicId);
    }
}