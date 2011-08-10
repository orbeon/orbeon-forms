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
        xmlns:sql="http://orbeon.org/oxf/xml/sql"
        xmlns:odt="http://orbeon.org/oxf/xml/datatypes"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:saxon="http://saxon.sf.net/"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events">

    <!-- Search instance -->
    <p:param name="instance" type="input"/>

    <!-- Search result -->
    <p:param name="data" type="output"/>

    <!-- Prepare submission -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <xforms:submission xsl:version="2.0" method="get"
                               resource="{{xxforms:property('oxf.fr.persistence.service.exist.uri')}}/{/*/app
                                            }/?_howmany={/*/page-size}&amp;_start={/*/page-number}" replace="instance">
                <!-- Move resulting <document> element as root element -->
                <xforms:insert ev:event="xforms-submit-done" nodeset="/*" origin="/*/*[1]"/>
                <!-- Log and propagate error to caller -->
                <xforms:action ev:event="xforms-submit-error" xmlns:form-runner="java:org.orbeon.oxf.fr.FormRunner">
                    <xforms:message level="xxforms:log-debug"><xforms:output value="event('response-body')"/></xforms:message>
                    <xforms:action type="xpath">form-runner:sendError((event('response-status-code'), 500)[1])</xforms:action>
                </xforms:action>
            </xforms:submission>
        </p:input>
        <p:output name="data" id="submission"/>
    </p:processor>

    <!-- Execute REST submission -->
    <p:processor name="oxf:xforms-submission">
        <p:input name="submission" href="#submission"/>
        <p:input name="request" href="#instance"/>
        <p:output name="response" id="response"/>
    </p:processor>

    <!-- Format response -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#response"/>
        <p:input name="config">
            <forms xsl:version="2.0" xmlns:exist="http://exist.sourceforge.net/NS/exist">
                <xsl:for-each select="/exist:collection/exist:collection">
                    <form name="{@name}" created="{@created}"/>
                </xsl:for-each>
            </forms>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
