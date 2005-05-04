<!--
    Copyright (C) 2005 Orbeon, Inc.

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
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xforms="http://www.w3.org/2002/xforms">

    <p:param name="request" type="input" schema-href="xforms-server-request.rng"/>
    <p:param name="response" type="output" schema-href="xforms-server-response.rng"/>

    <!-- Execute event action -->
    <p:processor name="oxf:xforms-server">
        <p:input name="instances" href="#request#xpointer(/xxforms:event-fired/xxforms:instances)"/>
        <p:input name="models" href="#request#xpointer(/xxforms:event-fired/xxforms:models)"/>
        <p:input name="controls" href="#request#xpointer(/xxforms:event-fired/xxforms:controls)"/>
        <p:input name="event" href="#request#xpointer(/xxforms:event-fired/xxforms:event)"/>

        <p:output name="response" ref="response"/>
    </p:processor>

</p:config>
