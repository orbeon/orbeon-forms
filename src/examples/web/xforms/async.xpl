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
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:ev="http://www.w3.org/2001/xml-events">

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
        <p:output name="data" id="xml-request" debug="xxxrequest"/>
    </p:processor>

    <!-- Extract relevant action from event document -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#xml-request"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:template match="/">
                    <xsl:variable name="event-name" select="/xxforms:event-fired/xxforms:event/@name" as="xs:string"/>
                    <xsl:variable name="event-control-id" select="/xxforms:event-fired/xxforms:event/@source-control-id" as="xs:string"/>
    <!--                <xsl:variable name="instance" select="/xxforms:event-fired/xxforms:instance" as="document-node()"/>-->
    <!--                <xsl:variable name="model" select="/xxforms:event-fired/xxforms:model" as="document-node()"/>-->
                    <xsl:variable name="action-control" select="/xxforms:event-fired/xxforms:controls/*[xxforms:id = $event-control-id]" as="element()"/>
                    <xsl:variable name="action" select="$action-control/xforms:*[@ev:event = $event-name]" as="element()"/>

                    <xsl:copy-of select="$action"/>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="xforms-action"/>
    </p:processor>

    <!-- Execute event action -->
    <p:processor name="oxf:xforms-event">
        <p:input name="instance" href="#xml-request#xpointer(/xxforms:event-fired/xxforms:instance)"/>
        <p:input name="model" href="#xml-request#xpointer(/xxforms:event-fired/xxforms:model)"/>
        <p:input name="action" href="#xforms-action"/>

        <p:output name="response" id="xml-response"/>
    </p:processor>

    <!-- Generate response -->
    <p:processor name="oxf:xml-serializer">
        <p:input name="data" href="#xml-response" debug="xxxresponse"/>
        <p:input name="config">
            <config/>
        </p:input>
    </p:processor>

</p:config>
