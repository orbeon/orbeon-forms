<!--
    Copyright (C) 2004 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input" name="instance"/>
    <p:param type="input" name="data"/>
    <p:param type="output" name="data"/>

    <p:choose href="#instance">
        <p:when test="/form/pdf = 'true'">
            <!-- Produce XSL-FO -->

            <p:processor name="oxf:xslt">
                <p:input name="data" href="#data"/>
                <p:input name="config" href="skin/xslt/fo/document2fo.xsl"/>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Produce XHTML -->

            <p:processor name="oxf:xslt">
                <p:input name="config" href="doc-view-html.xsl"/>
                <p:input name="instance" href="#instance"/>
                <p:input name="data" href="#data"/>
                <p:output name="data" id="view"/>
            </p:processor>

            <!-- The doc does not necessarily contain only XHTML, so we convert it -->
            <p:processor name="oxf:qname-converter">
                <p:input name="config">
                    <config>
                        <match>
                            <uri></uri>
                        </match>
                        <replace>
                            <uri>http://www.w3.org/1999/xhtml</uri>
                            <prefix>xhtml</prefix>
                        </replace>
                    </config>
                </p:input>
                <p:input name="data" href="#view"/>
                <p:output name="data" ref="data"/>
            </p:processor>

        </p:otherwise>
    </p:choose>

</p:config>
