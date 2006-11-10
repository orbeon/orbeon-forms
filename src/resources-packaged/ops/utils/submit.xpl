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

<!--
    This pipeline performs a submission using the XForms server. For backward compatibility only. See
    oxf:xforms-submission instead.
-->
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

    <p:processor name="oxf:xforms-submission">
        <p:input name="submission" href="#submission"/>
        <p:input name="request" href="#request"/>
        <p:output name="response" ref="response"/>
    </p:processor>

</p:config>
