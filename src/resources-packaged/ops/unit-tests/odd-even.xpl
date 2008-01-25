<!--
    Copyright (C) 2004 Orbeon, Inc.

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
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input" name="number"/>
    <p:param type="output" name="data"/>

    <p:choose href="#number">
        <p:when test="number mod 2 = 1">
            <p:processor name="oxf:identity">
                <p:input name="data"><root>odd</root></p:input>
                <p:output name="data" id="result"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <p:processor name="oxf:identity">
                <p:input name="data"><root>even</root></p:input>
                <p:output name="data" id="result"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <p:processor name="oxf:identity">
        <p:input name="data" href="#result"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
