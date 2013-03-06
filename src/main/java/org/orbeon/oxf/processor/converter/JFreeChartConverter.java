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
package org.orbeon.oxf.processor.converter;

import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.serializer.legacy.JFreeChartSerializer;

/**
 * Converts XML into a binary chart image.
 */
public class JFreeChartConverter extends JFreeChartSerializer {

    public static final String CHART_CONVERTER_CONFIG_NAMESPACE_URI = "http://www.orbeon.com/oxf/converter/chart-config";

    public JFreeChartConverter() {
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    protected String getConfigSchemaNamespaceURI() {
        return CHART_CONVERTER_CONFIG_NAMESPACE_URI;
    }
}
