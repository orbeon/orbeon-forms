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

<!--
    This pipeline performs a submission using the XForms server. Do not use it directly, but can call it using the
    oxf:xforms-submission processor:

    <p:processor name="oxf:xforms-submission">
        <p:input name="submission">
            <xforms:submission method="post" action="/direct/xforms-translate/post"/>
        </p:input>
        <p:input name="request">
            <translation>
                <source>This is a table.</source>
                <language-pair>en|fr</language-pair>
            </translation>
        </p:input>
        <p:output name="response" id="response"/>
    </p:processor>
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary">

    <p:param name="submission" type="input"/>
    <p:param name="request" type="input"/>
    <p:param name="response" type="output"/>

    <!-- Encode -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="request" href="#request"/>
        <p:input name="submission" href="#submission"/>
        <p:input name="data"><dummy/></p:input>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:output name="xml-output" method="xml" version="1.0" encoding="UTF-8" indent="no" omit-xml-declaration="no"/>

                <xsl:variable name="uuid" select="uuid:createPseudoUUID()" xmlns:uuid="org.orbeon.oxf.util.UUIDUtils"/>
                <xsl:variable name="is-shared-result" select="exists(doc('input:submission')/xforms:submission[@xxforms:shared = 'application' or @xxforms:cache = 'true'])" as="xs:boolean"/>
                
                <xsl:variable name="static-state" as="document-node()">
                    <xsl:document>
                        <static-state>
                            <xforms:trigger id="fr-trigger">
                                <xforms:send id="fr-send" submission="fr-default-submission" ev:event="DOMActivate"/>
                            </xforms:trigger>
                            <xforms:model id="fr-model">
                                <xforms:instance id="fr-instance">
                                    <dummy/>
                                </xforms:instance>
                                <xsl:if test="$is-shared-result">
                                    <xforms:instance id="fr-result-instance">
                                        <dummy/>
                                    </xforms:instance>
                                </xsl:if>
                                <xforms:submission id="fr-default-submission" replace="instance">
                                    <xsl:copy-of select="doc('input:submission')/xforms:submission/@*[local-name() != 'id']"/>
                                    <xsl:copy-of select="doc('input:submission')/xforms:submission/namespace::*"/>
                                    <xsl:copy-of select="doc('input:submission')/xforms:submission/*"/>
                                    <!-- If the result is shared, it's not directly present in the dynamic state, so we copy it to another instance -->
                                    <xsl:if test="$is-shared-result">
                                        <xforms:insert ev:event="xforms-submit-done" origin="instance('fr-instance')" nodeset="instance('fr-result-instance')"/>
                                    </xsl:if>
                                </xforms:submission>
                            </xforms:model>
                            <properties xxforms:state-handling="client" xxforms:noscript="false" xxforms:forward-submission-headers="{xpl:property('oxf.xforms.forward-submission-headers')}"
                                        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms" xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary"/>
                            <last-id id="1000"/>
                        </static-state>
                    </xsl:document>
                </xsl:variable>
                
                <xsl:variable name="dynamic-state" as="document-node()">
                    <xsl:document>
                        <dynamic-state uuid="{$uuid}" sequence="1">
                            <instances>
                                <instance id="fr-instance" model-id="fr-model">
                                    <xsl:value-of select="saxon:serialize(doc('input:request'), 'xml-output')"/>
                                </instance>
                                <xsl:if test="$is-shared-result">
                                    <instance id="fr-result-instance" model-id="fr-model">&lt;dummy/></instance>
                                </xsl:if>
                            </instances>
                        </dynamic-state>
                    </xsl:document>
                </xsl:variable>

                <xsl:template match="/*/xforms:model/xforms:submission//*[not(exists(@id))]" mode="update-ids">
                    <xsl:copy>
                        <xsl:attribute name="id" select="concat('xf-', count(preceding::*) + 1)"/>
                        <xsl:apply-templates select="@*|node()" mode="#current"/>
                    </xsl:copy>
                </xsl:template>

                <xsl:template match="@*|node()" mode="update-ids">
                    <xsl:copy>
                        <xsl:apply-templates select="@*|node()" mode="#current"/>
                    </xsl:copy>
                </xsl:template>
                
                <xsl:template match="/">

                    <!-- Add ids as oxf:xforms-server expects ids on all XForms elements and caller might not have put ids everywhere -->
                    <xsl:variable name="annotated-static-state" as="document-node()">
                        <xsl:document>
                            <xsl:apply-templates select="$static-state" mode="update-ids"/>
                        </xsl:document>
                    </xsl:variable>

                    <!-- A bit of a hack: oxf:xforms-server expects this value in the session to enforce session association -->
                    <xsl:value-of select="manager:addDocumentToSession($uuid)" xmlns:manager="java:org.orbeon.oxf.xforms.state.XFormsStateManager"/>

                    <xxforms:event-request>
                        <xxforms:uuid><xsl:value-of select="$uuid"/></xxforms:uuid>
                        <xxforms:sequence>1</xxforms:sequence>
                        <xxforms:static-state>
                            <xsl:value-of select="xpl:encodeXML($annotated-static-state)" xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary"/>
                        </xxforms:static-state>
                        <xxforms:dynamic-state>
                            <xsl:value-of select="xpl:encodeXML($dynamic-state)" xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary"/>
                        </xxforms:dynamic-state>
                        <xxforms:action>
                            <xxforms:event name="DOMActivate" source-control-id="fr-trigger"/>
                        </xxforms:action>
                    </xxforms:event-request>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="encoded-request"/>
    </p:processor>

    <!-- Run XForms Server -->
    <p:processor name="oxf:xforms-server">
        <p:input name="request" href="#encoded-request" schema-href="/ops/xforms/xforms-server-request.rng"/>
        <p:output name="response" id="encoded-response" schema-href="/ops/xforms/xforms-server-response.rng"/>
    </p:processor>

    <!-- Decode -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#encoded-response"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="/">
                    <xsl:variable name="dynamic-state" select="xpl:decodeXML(normalize-space(xxforms:event-response/xxforms:dynamic-state))" as="document-node()"/>

                    <!-- A bit of a hack: oxf:xforms-server expects this value in the session to enforce session association -->
                    <!-- Remove it here so as to not grow the session each time a submission is called. -->
                    <xsl:value-of select="manager:removeSessionDocument($dynamic-state/*/@uuid)" xmlns:manager="java:org.orbeon.oxf.xforms.state.XFormsStateManager"/>
                    
                    <!-- Copy out the last instance from the dynamic state -->
                    <xsl:copy-of select="saxon:parse($dynamic-state/dynamic-state/instances/instance[last()])"/>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="response"/>
    </p:processor>

</p:config>
