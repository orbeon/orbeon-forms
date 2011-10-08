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
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

    <!-- PDF document in binary form -->
    <p:param type="output" name="data"/>

    <!-- Extract request parameters (app, form, document, and mode) from URL -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../request-parameters.xpl"/>
        <p:output name="data" id="parameters"/>
    </p:processor>

    <!--
        Create a special XForms server request which:

        1. Passes in the UUID of the live XForms document in
        2. Doesn't specify a sequence number as we don't want to increment it
        3. Dispatches an event to activate PDF generation
        4. Form Runner will call a submission with replace="all"
        5. Submission has 2-pass mode disabled as we are in an HTTP GET
        6. In case of success, the output of the XForms processor is the PDF document ready to be sent out
     -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#parameters"/>
        <p:input name="config">
            <xxforms:event-request xsl:version="2.0">
                <!-- UUID is passed in the URL -->
                <xxforms:uuid><xsl:value-of select="/*/uuid"/></xxforms:uuid>
                <!-- No sequence number indicates we don't want to increment it -->
                <xxforms:sequence/>
                <!-- Event is authorized explicitly in view/edit modes -->
                <xxforms:action>
                    <xxforms:event name="fr-open-pdf" source-control-id="fr-navigation-model"/>
                </xxforms:action>
            </xxforms:event-request>
        </p:input>
        <p:output name="data" id="xforms-request"/>
    </p:processor>

    <!-- Run XForms Server -->
    <p:processor name="oxf:xforms-server">
        <p:input name="request" href="#xforms-request" schema-href="/ops/xforms/xforms-server-request.rng"/>
        <p:output name="response" id="xforms-response" ref="data"/>
    </p:processor>

</p:config>
