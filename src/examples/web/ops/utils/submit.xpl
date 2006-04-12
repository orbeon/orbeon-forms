<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:saxon="http://saxon.sf.net/">

    <p:param name="submission" type="input"/>
    <p:param name="request" type="input"/>
    <p:param name="response" type="output"/>

    <!-- Encode -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="request" href="#request"/>
        <p:input name="submission" href="#submission"/>
        <p:input name="data"><dummy/></p:input>
        <p:input name="config">
            <xxforms:event-request xsl:version="2.0" xmlns:context="java:org.orbeon.oxf.pipeline.StaticExternalContext">
                <xxforms:static-state>
                    <xsl:variable name="static-state" as="document-node()">
                        <xsl:document>
                            <xxforms:static-state xxforms:state-handling="client">
                                <xxforms:controls>
                                    <xforms:trigger id="trigger">
                                        <xforms:send submission="post" ev:event="DOMActivate"/>
                                    </xforms:trigger>
                                </xxforms:controls>
                                <xxforms:models>
                                    <xforms:model>
                                        <xforms:instance>
                                            <xsl:copy-of select="doc('input:request')"/>
                                        </xforms:instance>
                                        <xforms:submission id="post" replace="instance">
                                            <xsl:copy-of select="doc('input:submission')/xforms:submission/@*[local-name() != 'id' and local-name() != 'id']"/>
                                        </xforms:submission>
                                    </xforms:model>
                                </xxforms:models>
                            </xxforms:static-state>
                        </xsl:document>
                    </xsl:variable>
                    <xsl:value-of select="context:encodeXML($static-state)"/>
                </xxforms:static-state>
                <xxforms:dynamic-state>
                    <xsl:variable name="dynamic-state" as="document-node()">
                        <xsl:document>
                            <xxforms:dynamic-state>
                                <xxforms:instances>
                                    <xsl:copy-of select="doc('input:request')"/>
                                </xxforms:instances>
                            </xxforms:dynamic-state>
                        </xsl:document>
                    </xsl:variable>
                    <xsl:value-of select="context:encodeXML($dynamic-state)"/>
                </xxforms:dynamic-state>
                <xxforms:action>
                    <xxforms:event name="DOMActivate" source-control-id="trigger"/>
                </xxforms:action>
            </xxforms:event-request>
        </p:input>
        <p:output name="data" id="encoded-request" debug="request"/>
    </p:processor>

    <!-- Run XForms Server -->
    <p:processor name="oxf:xforms-server">
        <p:input name="request" href="#encoded-request" schema-href="/ops/xforms/xforms-server-request.rng"/>
        <p:output name="response" id="encoded-response" schema-href="/ops/xforms/xforms-server-response.rng" debug="response"/>
    </p:processor>

    <!-- Decode -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#encoded-response"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:context="java:org.orbeon.oxf.pipeline.StaticExternalContext">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="/">
                    <xsl:copy-of select="context:decodeXML(normalize-space(xxforms:event-response/xxforms:dynamic-state))/dynamic-state/instances/*"/>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="response"/>
    </p:processor>

</p:config>
