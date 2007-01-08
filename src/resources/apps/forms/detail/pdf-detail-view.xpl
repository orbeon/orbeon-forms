<!--
    Copyright (C) 2006 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <!-- Create REST submission -->
    <p:processor name="oxf:xslt">
        <p:input name="config">
            <xforms:submission xmlns:xforms="http://www.w3.org/2002/xforms" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                               xsl:version="2.0" serialize="false"
                               method="get" action="/exist/rest/db/orbeon/forms/{/*/form-id}/{/*/document-id}"/>
        </p:input>
        <p:input name="data" href="#instance"/>
        <p:output name="data" id="submission"/>
    </p:processor>

    <!-- Execute REST submission -->
    <p:processor name="oxf:xforms-submission">
        <p:input name="submission" href="#submission"/>
        <p:input name="request"><dummy/></p:input>
        <p:output name="response" id="document"/>
    </p:processor>
    
    <!-- Produce PDF document -->
    <p:processor name="oxf:pdf-template">
        <p:input name="instance" href="#document"/>
        <p:input name="model" href="#instance#xpointer(doc(concat('../forms/', /*/form-id, '/pdf-template.xml')))"/>
        <!-- TODO: Remove this input -->
        <p:input name="config"><config/></p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
