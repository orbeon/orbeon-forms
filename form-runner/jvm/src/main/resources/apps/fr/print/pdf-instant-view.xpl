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
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms">

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
            <xxf:event-request xsl:version="2.0">
                <!-- UUID is passed in the URL -->
                <xxf:uuid><xsl:value-of select="/*/uuid"/></xxf:uuid>
                <!-- No sequence number indicates we don't want to increment it -->
                <xxf:sequence/>
                <!-- Event is authorized explicitly in view/edit modes -->
                <xxf:action>
                    <xxf:event name="fr-open-pdf" source-control-id="fr-pdf-model">
                        <xxf:property name="fr-format"><xsl:value-of select="/*/mode"/></xxf:property>
                        <xxf:property name="fr-language"><xsl:value-of select="p:get-request-parameter('fr-language')"/></xxf:property>
                    </xxf:event>
                </xxf:action>
            </xxf:event-request>
        </p:input>
        <p:output name="data" id="xforms-request"/>
    </p:processor>

    <!-- Run XForms Server -->
    <p:processor name="oxf:xforms-server">
        <p:input name="request" href="#xforms-request" schema-href="/ops/xforms/xforms-server-request.rng"/>
        <p:output name="response" id="xforms-response" ref="data"/>
    </p:processor>

</p:config>
