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
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

    <!-- Extract parameters -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
                <include>/request/container-type</include>
                <include>/request/parameters</include>
            </config>
        </p:input>
        <p:output name="data" id="request-params"/>
    </p:processor>

    <!-- Second pass of a submission with replace="all" -->
    <!-- Create XForms Server request -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#request-params"/>
        <p:input name="config">
            <xxf:event-request xsl:version="2.0" xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
                <xxf:uuid>
                    <xsl:value-of select="/*/parameters/parameter[name = '$uuid']/value"/>
                </xxf:uuid>
                <!-- Omit sequence number -->
                <xxf:sequence/>
                <xxf:static-state>
                    <xsl:value-of select="/*/parameters/parameter[name = '$static-state']/value"/>
                </xxf:static-state>
                <xxf:dynamic-state>
                    <xsl:value-of select="/*/parameters/parameter[name = '$dynamic-state']/value"/>
                </xxf:dynamic-state>
                <xxf:action/>
            </xxf:event-request>
        </p:input>
        <p:output name="data" id="xml-request"/>
    </p:processor>

    <!-- Run XForms Server -->
    <p:processor name="oxf:xforms-server">
        <p:input name="request" href="#xml-request" schema-href="xforms-server-request.rng"/>
    </p:processor>

</p:config>
