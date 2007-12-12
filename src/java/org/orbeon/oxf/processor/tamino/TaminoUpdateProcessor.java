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
package org.orbeon.oxf.processor.tamino;

import com.softwareag.tamino.db.api.accessor.TXMLObjectAccessor;
import com.softwareag.tamino.db.api.accessor.TXQuery;
import com.softwareag.tamino.db.api.connection.TConnection;
import com.softwareag.tamino.db.api.objectModel.TXMLObjectModel;
import com.softwareag.tamino.db.api.response.TResponse;
import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.CacheableInputReader;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.tamino.dom4j.TDOM4JObjectModel;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

public class TaminoUpdateProcessor extends TaminoProcessor {

    static {
        TXMLObjectModel.register(TDOM4JObjectModel.getInstance());
    }

    public TaminoUpdateProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, TAMINO_CONFIG_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA, TAMINO_QUERY_URI));
    }

    public void start(PipelineContext context) {
        try {
            // Read configuration
            final Config config = (Config) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                public Object read(org.orbeon.oxf.pipeline.api.PipelineContext context, ProcessorInput input) {
                    return readConfig(readInputAsDOM4J(context, INPUT_CONFIG));
                }
            });

            // Read data
            final Document queryDocument = readInputAsDOM4J(context, INPUT_DATA);

            final TConnection connection = getConnection(context, config);
            final TXMLObjectAccessor accessor = connection.newXMLObjectAccessor(config.getCollection(), TDOM4JObjectModel.getInstance());

            final TXQuery xquery = TXQuery.newInstance(Dom4jUtils.objectToString(XPathUtils.selectObjectValue(queryDocument, "/xquery/text() | /xquery/*")));
            if (logger.isDebugEnabled())
                logger.debug("Tamino - Update - expression:\n" + xquery.getExpression());
            final TResponse response = accessor.xquery(xquery);
            if (logger.isDebugEnabled())
                logger.debug("Tamino - Update - response:\n" + response.getQueryContentAsString());

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
