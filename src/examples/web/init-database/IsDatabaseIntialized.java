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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.SimpleProcessor;
import org.orbeon.oxf.processor.pipeline.PipelineContext;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

public class IsDatabaseIntialized extends SimpleProcessor {

    public IsDatabaseIntialized() {
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public void generateData(PipelineContext context,
                             ContentHandler contentHandler) throws SAXException {
        try {
            Connection connection = null;
            try {

                // Determine if we have tables in the database
                final boolean hasTables;
                {
                    DataSource dataSource = (DataSource) new InitialContext().lookup("java:comp/env/jdbc/db");
                    connection = dataSource.getConnection();
                    DatabaseMetaData metaData = connection.getMetaData();
                    ResultSet tables = metaData.getTables(null, null, null, null);
                    hasTables = tables.next();
                }

                // Send the result as XML
                ContentHandlerHelper contentHandlerHelper = new ContentHandlerHelper(contentHandler);
                contentHandlerHelper.startDocument();
                contentHandlerHelper.startElement("is-initialiazed");
                contentHandlerHelper.text(Boolean.toString(hasTables));
                contentHandlerHelper.endElement();
                contentHandlerHelper.endDocument();

            } finally {
                if (connection != null) connection.close();
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}