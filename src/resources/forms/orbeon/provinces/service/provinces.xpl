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
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="countries.xpl"/>
        <p:output name="data" id="countries"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#countries"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="config">
            <provinces xsl:version="2.0">
                <xsl:copy-of select="/*/country[@code = doc('input:instance')/*]/province"/>
            </provinces>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
