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
    <p:param type="output" name="data"/>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/request-path</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:perl5-matcher">
        <p:input name="config"><config>/fr/service/crud/([^/]+)/([^/]+)/([^/]+)</config></p:input>
        <p:input name="data" href="#request#xpointer(/request/request-path)"/>
        <p:output name="data" id="matcher-groups"/>
    </p:processor>

    <p:processor name="oxf:xforms-submission">
        <p:input name="submission">
            <xforms:submission ref="/*/*[1]" method="put" replace="none"
                    resource="{xxforms:property('oxf.fr.persistence.exist.uri')}/{/*/group[2]}/{/*/group[3]}/{digest(string(random(true)), 'MD5', 'hex')}">
                <xforms:action ev:event="xforms-submit-error">
                    <!-- TODO: Propagate error to caller -->
                    <xforms:delete while="/*/*" nodeset="/*/*"/>
                    <xforms:setvalue ref="/*" value="event('response-body')"/>
                    <xforms:message level="xxforms:log-error"><xforms:output value="event('response-body')"/></xforms:message>
                </xforms:action>
                <xforms:action ev:event="xforms-submit-done">
                    <xforms:delete while="/*/*" nodeset="/*/*"/>
                    <xforms:setvalue ref="/*" value="tokenize(event('resource-uri'), '/')[last()]"/>
                </xforms:action>
            </xforms:submission>
        </p:input>
        <p:input name="request" href="aggregate('id', #instance, #matcher-groups#xpointer(/*/group))"/>
        <p:output name="response" ref="data"/>
    </p:processor>

</p:config>
