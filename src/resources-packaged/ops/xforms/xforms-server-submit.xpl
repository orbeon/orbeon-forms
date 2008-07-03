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

    <!-- Extract parameters -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
                <include>/request/parameters</include>
            </config>
        </p:input>
        <!--<p:output name="data" id="request-params" debug="xxxrequest-params"/>-->
        <p:output name="data" id="request-params"/>
    </p:processor>

    <p:choose href="#request-params">
        <!-- Second pass of a submission with replace="all" -->
        <p:when test="not(/*/parameters/parameter[name = '$noscript']/value = 'true')">
            <!-- Create XForms Server request -->
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#request-params"/>
                <p:input name="config">
                    <xxforms:event-request xsl:version="2.0" xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">
                        <xxforms:static-state>
                            <xsl:value-of select="/*/parameters/parameter[name = '$static-state']/value"/>
                        </xxforms:static-state>
                        <xxforms:dynamic-state>
                            <xsl:value-of select="/*/parameters/parameter[name = '$dynamic-state']/value"/>
                        </xxforms:dynamic-state>
                        <xsl:variable name="files" select="/*/parameters/parameter[filename]"/>
                        <xsl:if test="$files">
                            <xxforms:files>
                                <xsl:copy-of select="$files"/>
                            </xxforms:files>
                        </xsl:if>
                        <xxforms:action/>
                        <xsl:variable name="server-events" select="/*/parameters/parameter[name = '$server-events']/value"/>
                        <xsl:if test="not($server-events = '')">
                            <xxforms:server-events>
                                <xsl:value-of select="$server-events"/>
                            </xxforms:server-events>
                        </xsl:if>
                    </xxforms:event-request>
                </p:input>
                <!--<p:output name="data" id="xml-request" debug="xxxsubmit-request"/>-->
                <p:output name="data" id="xml-request"/>
            </p:processor>

            <!-- Run XForms Server -->
            <p:processor name="oxf:xforms-server">
                <p:input name="request" href="#xml-request" schema-href="xforms-server-request.rng"/>
            </p:processor>
        </p:when>
        <!-- Noscript mode response -->
        <p:otherwise>
            <!-- Create XForms Server request -->
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#request-params"/>
                <p:input name="config">
                    <xxforms:event-request xsl:version="2.0" xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">
                        <xxforms:static-state>
                            <xsl:value-of select="/*/parameters/parameter[name = '$static-state']/value"/>
                        </xxforms:static-state>
                        <xxforms:dynamic-state>
                            <xsl:value-of select="/*/parameters/parameter[name = '$dynamic-state']/value"/>
                        </xxforms:dynamic-state>
                        <xsl:variable name="files" select="/*/parameters/parameter[filename]"/>
                        <xsl:if test="$files">
                            <xxforms:files>
                                <xsl:copy-of select="$files"/>
                            </xxforms:files>
                        </xsl:if>
                        <xxforms:action>
                            <!-- Create list of events based on parameters -->
                            <xsl:for-each select="/*/parameters/parameter[not(starts-with(name, '$') or filename)]">
                                <!-- Here we don't know the type of the control so can't create the proper type of event -->
                                <xxforms:event name="xxforms-value-or-activate" source-control-id="{name}">
                                    <xsl:value-of select="value"/>
                                </xxforms:event>
                            </xsl:for-each>
                        </xxforms:action>
                        <xsl:variable name="server-events" select="/*/parameters/parameter[name = '$server-events']/value"/>
                        <xsl:if test="$server-events != ''">
                            <xxforms:server-events>
                                <xsl:value-of select="$server-events"/>
                            </xxforms:server-events>
                        </xsl:if>
                    </xxforms:event-request>
                </p:input>
                <!--<p:output name="data" id="xml-request" debug="xxxsubmit-request"/>-->
                <p:output name="data" id="xml-request"/>
            </p:processor>

            <!-- Run XForms Server -->
            <p:processor name="oxf:xforms-server">
                <p:input name="request" href="#xml-request" schema-href="xforms-server-request.rng"/>
                <p:output name="response" id="xformed-data"/>
            </p:processor>

            <!-- Call epilogue -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="/config/epilogue-servlet.xpl"/>
                <p:input name="data"><null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/></p:input>
                <p:input name="xformed-data" href="#xformed-data"/>
                <p:input name="instance"><null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/></p:input>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
