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
<p:pipeline xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xforms="http://www.w3.org/2002/xforms">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <!-- Execute REST submission -->
    <p:processor name="oxf:xforms-submission">
        <p:input name="submission">
            <xforms:submission serialization="none" method="get"
                               action="/exist/rest/db/orbeon/forms/{/*/form-id}/{/*/document-id}"/>
        </p:input>
        <p:input name="request" href="#instance"/>
        <p:output name="response" id="document"/>
    </p:processor>
    
    <!-- Produce PDF document -->
    <p:processor name="oxf:pdf-template">
        <p:input name="instance" href="#document"/>
        <p:input name="model" href="#instance#xpointer(doc(concat('../forms/', /*/form-id, '/pdf-model.xml')))"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:pipeline>
