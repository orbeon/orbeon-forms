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
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:xh="http://www.w3.org/1999/xhtml">

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

                    <xh:html>
                        <xh:head>
                            <!-- Models -->
                            <xsl:apply-templates select="doc('input:models')/*/*"/>
                        </xh:head>
                        <xh:body>
                            <!-- Controls -->
                            <xsl:apply-templates select="doc('input:controls')/*/*"/>
                        </xh:body>
                    </xh:html>
                    
                </xsl:template>

                <!-- Copy instance content for matching instances -->
                <xsl:template match="xf:instance[@id = $instance-ids]">
                    <xsl:variable name="id" select="@id"/>
                    <xsl:variable name="model-id" select="parent::xf:model/@id"/>

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
                    <xxf:event-request>
                        <xxf:uuid><xsl:value-of select="//xh:input[@name = '$uuid']/@value"/></xxf:uuid>
                        <xxf:sequence>1</xxf:sequence>
                        <xxf:static-state><xsl:value-of select="//xh:input[@name = '$static-state']/@value"/></xxf:static-state>
                        <xxf:dynamic-state><xsl:value-of select="//xh:input[@name = '$dynamic-state']/@value"/></xxf:dynamic-state>
                        <xxf:action>
                            <xsl:copy-of select="doc('input:action')/*/*"/>
                        </xxf:action>
                    </xxf:event-request>
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
