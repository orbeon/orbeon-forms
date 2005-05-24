<!--
    Copyright (C) 2005 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- Extract request body -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
                <include>/request/body</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Dereference URI and return XML -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" href="aggregate('config', aggregate('url', #request#xpointer(string(/request/body))))"/>
        <p:output name="data" id="xml-request"/>
    </p:processor>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="translate.xpl"/>
        <p:input name="source" href="#xml-request#xpointer(/translation/source)"/>
        <p:input name="language-pair" href="#xml-request#xpointer(/translation/language-pair)"/>
        <p:output name="data" id="target"/>
    </p:processor>
    
    <!-- Build respnse -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#xml-request"/>
        <p:input name="target" href="#target"/>
        <p:input name="config">
            <xsl:transform version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="target">
                    <xsl:copy>
                        <xsl:value-of select="doc('input:target')"/>
                    </xsl:copy>
                </xsl:template>
            </xsl:transform>
        </p:input>
        <p:output name="data" id="xml-response"/>
    </p:processor>

    <!-- Generate response -->
    <p:processor name="oxf:xml-serializer">
        <p:input name="data" href="#xml-response"/>
        <p:input name="config">
            <config>
                <content-type>application/xml</content-type>
            </config>
        </p:input>
    </p:processor>

</p:config>
