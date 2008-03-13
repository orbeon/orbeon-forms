<?xml version="1.0" encoding="utf-8"?>
<!--
    Copyright (C) 2008 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:sql="http://orbeon.org/oxf/xml/sql"
        xmlns:odt="http://orbeon.org/oxf/xml/datatypes"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events">

    <p:param type="input" name="instance"/>

    <!-- NOTE: It's disappointing that we have to use oxf:request/oxf:perl5-matcher rather than using the page flow
         directly, but because we want to support the PUT and POST methods, this is currently the only solution. -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/request-path</include>
                <include>/request/content-type</include>
                <include>/request/method</include>
                <include>/request/body</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:perl5-matcher">
        <p:input name="config"><config>/fr/service/exist/crud/([^/]+/[^/]+/(form/[^/]+|data/[^/]+/[^/]+))</config></p:input>
        <p:input name="data" href="#request#xpointer(/request/request-path)"/>
        <p:output name="data" id="matcher-groups"/>
    </p:processor>

    <!-- Discriminate based on the HTTP method and content type -->
    <p:choose href="#request">

        <!-- Binary put -->
        <p:when test="/*/method = 'PUT' and not(/*/content-type = ('application/xml', 'text/xml') or ends-with(/*/content-type, '+xml'))">
            
            <p:processor name="oxf:xforms-submission">
                <p:input name="submission">
                    <xforms:submission ref="/*/body" method="put" replace="none"
                            serialization="application/octet-stream"
                            resource="{xxforms:property('oxf.fr.persistence.service.exist.uri')}/{/*/group[1]}">
                        <xforms:action ev:event="xforms-submit-error">
                            <!-- TODO: Propagate error to caller -->
                            <xforms:delete while="/*/*" nodeset="/*/*"/>
                            <xforms:setvalue ref="/*" value="event('response-body')"/>
                            <xforms:message level="xxforms:log-error"><xforms:output value="event('response-body')"/></xforms:message>
                        </xforms:action>
                    </xforms:submission>
                </p:input>
                <p:input name="request" href="aggregate('root', #request#xpointer(/*/body), #matcher-groups#xpointer(/*/group))"/>
                <p:output name="response" id="response"/>
            </p:processor>

        </p:when>
        <p:when test="/*/method = 'GET'">

            <p:processor name="oxf:xforms-submission">
                <p:input name="submission">
                    <xforms:submission method="get" replace="instance" serialization="none"
                            resource="{xxforms:property('oxf.fr.persistence.service.exist.uri')}/{/*/group[1]}">
                        <xforms:action ev:event="xforms-submit-error">
                            <!-- TODO: Propagate error to caller -->
                            <xforms:delete while="/*/*" nodeset="/*/*"/>
                            <xforms:setvalue ref="/*" value="event('response-body')"/>
                            <xforms:message level="xxforms:log-error"><xforms:output value="event('response-body')"/></xforms:message>
                        </xforms:action>
                    </xforms:submission>
                </p:input>
                <p:input name="request" href="#matcher-groups"/>
                <p:output name="response" id="response"/>
            </p:processor>

        </p:when>
        <p:when test="/*/method = 'DELETE'">

            <p:processor name="oxf:xforms-submission">
                <p:input name="submission">
                    <xforms:submission method="delete" replace="none" serialization="none"
                            resource="{xxforms:property('oxf.fr.persistence.service.exist.uri')}/{/*/group[1]}">
                        <xforms:action ev:event="xforms-submit-error">
                            <!-- TODO: Propagate error to caller -->
                            <xforms:delete while="/*/*" nodeset="/*/*"/>
                            <xforms:setvalue ref="/*" value="event('response-body')"/>
                            <xforms:message level="xxforms:log-error"><xforms:output value="event('response-body')"/></xforms:message>
                        </xforms:action>
                    </xforms:submission>
                </p:input>
                <p:input name="request" href="#matcher-groups"/>
                <p:output name="response" id="response"/>
            </p:processor>

        </p:when>
        <p:when test="/*/method = 'PUT'">

            <p:processor name="oxf:xforms-submission">
                <p:input name="submission">
                    <xforms:submission ref="/*/*[1]" method="put" replace="none"
                            resource="{xxforms:property('oxf.fr.persistence.service.exist.uri')}/{/*/group[1]}">
                        <xforms:action ev:event="xforms-submit-error">
                            <!-- TODO: Propagate error to caller -->
                            <xforms:delete while="/*/*" nodeset="/*/*"/>
                            <xforms:setvalue ref="/*" value="event('response-body')"/>
                            <xforms:message level="xxforms:log-error"><xforms:output value="event('response-body')"/></xforms:message>
                        </xforms:action>
                    </xforms:submission>
                </p:input>
                <p:input name="request" href="aggregate('root', #instance, #matcher-groups#xpointer(/*/group))"/>
                <p:output name="response" id="response"/>
            </p:processor>
        </p:when>
    </p:choose>

    <!-- Convert and serialize to XML -->
    <p:processor name="oxf:xml-converter">
        <p:input name="config">
            <config>
                <indent>false</indent>
                <encoding>utf-8</encoding>
            </config>
        </p:input>
        <p:input name="data" href="#response"/>
        <p:output name="data" id="converted"/>
    </p:processor>

    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config>
                <cache-control>
                    <use-local-cache>false</use-local-cache>
                </cache-control>
            </config>
        </p:input>
        <p:input name="data" href="#converted"/>
    </p:processor>

</p:config>
