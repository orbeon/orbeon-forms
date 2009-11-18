<?xml version="1.0" encoding="UTF-8"?>
<!--
  Copyright (C) 2009 Orbeon, Inc.

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
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:saxon="http://saxon.sf.net/">

    <p:param name="action" type="input"/>
    <p:param name="controls" type="input"/>
    <p:param name="models" type="input"/>
    <p:param name="instances" type="input"/>

    <p:param name="response" type="output"/>

    <!-- Encode -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data"><dummy/></p:input>
        <p:input name="action" href="#action"/>
        <p:input name="controls" href="#controls"/>
        <p:input name="models" href="#models"/>
        <p:input name="instances" href="#instances"/>
        <p:input name="config">
            <xsl:transform version="2.0">
                <xsl:output method="xml" name="xml"/>
                <xsl:template match="/">
                    <event-request xmlns="http://orbeon.org/oxf/xml/xforms" xmlns:context="java:org.orbeon.oxf.pipeline.StaticExternalContext">
                        <static-state>
                            <xsl:variable name="static-state" as="document-node()">
                                <xsl:document>
                                    <static-state xmlns="">
                                        <xsl:copy-of select="doc('input:controls')/*/*"/>
                                        <xsl:copy-of select="doc('input:models')/*/*"/>
                                        <properties xxforms:state-handling="client"/>
                                        <last-id id="1000"/>
                                    </static-state>
                                </xsl:document>
                            </xsl:variable>
                            <xsl:value-of select="context:encodeXML($static-state)"/>
                        </static-state>
                        <dynamic-state>
                            <xsl:if test="doc('input:instances')/instances/instance">
                                <xsl:variable name="dynamic-state" as="document-node()">
                                    <xsl:document>
                                        <dynamic-state xmlns="">
                                            <instances>
                                                <xsl:for-each select="doc('input:instances')/instances/instance">
                                                    <xsl:copy>
                                                        <xsl:copy-of select="@*"/>
                                                        <xsl:value-of select="saxon:serialize(*[1], 'xml')"/>
                                                    </xsl:copy>
                                                </xsl:for-each>
                                            </instances>
                                        </dynamic-state>
                                    </xsl:document>
                                </xsl:variable>
                                <xsl:value-of select="context:encodeXML($dynamic-state)"/>
                            </xsl:if>
                        </dynamic-state>
                        <action>
                            <xsl:copy-of select="doc('input:action')/*/*"/>
                        </action>
                    </event-request>
                </xsl:template>
            </xsl:transform>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Run XForms Server -->
    <p:processor name="oxf:xforms-server">
        <p:input name="request" href="#request" schema-href="/ops/xforms/xforms-server-request.rng"/>
        <p:output name="response" id="encoded-response" schema-href="/ops/xforms/xforms-server-response.rng"/>
    </p:processor>

    <!-- Decode -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#encoded-response"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:context="java:org.orbeon.oxf.pipeline.StaticExternalContext">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="xxforms:static-state|xxforms:dynamic-state">
                    <xsl:copy>
                        <xsl:apply-templates select="context:decodeXML(normalize-space(.))/*"/>
                    </xsl:copy>
                </xsl:template>
                <xsl:template match="instances/instance">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <!-- Not sure why we can have the instance either serialized as text or directly inline! -->
                        <xsl:copy-of select="if (*) then * else saxon:parse(string(.))"/>
                    </xsl:copy>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="response"/>
    </p:processor>

</p:config>
