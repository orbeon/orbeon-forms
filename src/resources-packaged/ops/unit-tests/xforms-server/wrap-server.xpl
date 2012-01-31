<!--
  Copyright (C) 2010 Orbeon, Inc.

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
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:xhtml="http://www.w3.org/1999/xhtml">

    <p:param name="action" type="input"/>
    <p:param name="controls" type="input"/>
    <p:param name="models" type="input"/>
    <p:param name="instances" type="input"/>

    <p:param name="response" type="output"/>

    <!-- Create XHTML+XForms document based on separate models, instances, and controls -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data"><dummy/></p:input>
        <p:input name="controls" href="#controls"/>
        <p:input name="models" href="#models"/>
        <p:input name="instances" href="#instances"/>
        <p:input name="config">
            <xsl:transform version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>
                
                <xsl:variable name="model-ids" select="doc('input:instances')/*/instance/@model-id/string()"/>
                <xsl:variable name="instance-ids" select="doc('input:instances')/*/instance/@id/string()"/>

                <!-- Create XHTML+XForms document -->
                <xsl:template match="/">

                    <xhtml:html>
                        <xhtml:head>
                            <!-- Models -->
                            <xsl:apply-templates select="doc('input:models')/*/*"/>
                        </xhtml:head>
                        <xhtml:body>
                            <!-- Controls -->
                            <xsl:apply-templates select="doc('input:controls')/*/*"/>
                        </xhtml:body>
                    </xhtml:html>
                    
                </xsl:template>

                <!-- Copy instance content for matching instances -->
                <xsl:template match="xforms:instance[@id = $instance-ids]">
                    <xsl:variable name="id" select="@id"/>
                    <xsl:variable name="model-id" select="parent::xforms:model/@id"/>

                    <xsl:if test="not($model-id = $model-ids)">
                        <xsl:message terminate="yes">
                            Incorrect model id <xsl:value-of select="$model-id"/>
                            for instance id <xsl:value-of select="$id"/>.
                        </xsl:message>
                    </xsl:if>

                    <xsl:copy>
                        <xsl:apply-templates select="@*"/>
                        <xsl:apply-templates select="doc('input:instances')/*/instance[@id = $id]/node()"/>
                    </xsl:copy>
                </xsl:template>

            </xsl:transform>
        </p:input>
        <p:output name="data" id="document"/>
    </p:processor>

    <!-- Run XForms initialization -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="wrap-xforms-init-nofilter.xpl"/>
        <p:input name="document" href="#document"/>
        <p:output name="response" id="xhtml"/>
    </p:processor>

    <!-- Prepare Ajax request with the given actions -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#xhtml"/>
        <p:input name="action" href="#action"/>
        <p:input name="config">
            <xsl:transform version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

                <xsl:template match="/">
                    <xxforms:event-request>
                        <xxforms:uuid><xsl:value-of select="//xhtml:input[@name = '$uuid']/@value"/></xxforms:uuid>
                        <xxforms:sequence>1</xxforms:sequence>
                        <xxforms:static-state><xsl:value-of select="//xhtml:input[@name = '$static-state']/@value"/></xxforms:static-state>
                        <xxforms:dynamic-state><xsl:value-of select="//xhtml:input[@name = '$dynamic-state']/@value"/></xxforms:dynamic-state>
                        <xxforms:action>
                            <xsl:copy-of select="doc('input:action')/*/*"/>
                        </xxforms:action>
                    </xxforms:event-request>
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

    <!-- Decode the result -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#encoded-response"/>
        <p:input name="config" href="wrap-server-decode.xsl"/>
        <p:output name="data" ref="response"/>
    </p:processor>

</p:config>
