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
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <p:choose href="#instance">
        <p:when test="not(/null)">
            <p:processor name="oxf:scope-serializer">
                <p:input name="config">
                    <config>
                        <key>flickr-viewr-authentication</key>
                        <scope>session</scope>
                    </config>
                </p:input>
                <p:input name="data" href="#instance"/>
            </p:processor>
        </p:when>
    </p:choose>

    <p:processor name="oxf:scope-generator">
        <p:input name="config">
            <config>
                <key>flickr-viewr-authentication</key>
                <scope>session</scope>
            </config>
        </p:input>
        <p:output name="data" id="result"/>
    </p:processor>

     <p:choose href="#result">
        <p:when test="not(/null)">
            <p:processor name="oxf:identity">
                <p:input name="data" href="#result"/>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:when>
         <p:otherwise>
            <p:processor name="oxf:identity">
                <p:input name="data"><auth/></p:input>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
