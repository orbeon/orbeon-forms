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
<p:config xmlns:oxf="http://www.orbeon.com/oxf/processors" xmlns:p="http://www.orbeon.com/oxf/pipeline">
    <p:param name="data" type="output"/>
    <p:processor name="oxf:xinclude">
        <p:input name="config" href="oxf:/ops/unit-tests/cache/stylesheet2.xsl"/>
        <p:output name="data" id="stylesheet2"/>
    </p:processor>
    <p:processor name="oxf:xinclude">
        <p:input name="config" href="oxf:/ops/unit-tests/cache/b-2.xml"/>
        <p:output name="data" id="b-2"/>
    </p:processor>
    <p:processor name="oxf:xinclude">
        <p:input name="config" href="oxf:/ops/unit-tests/cache/c-2.xml"/>
        <p:output name="data" id="c-2"/>
    </p:processor>
    <p:processor name="oxf:xslt">
        <p:input name="config" href="#stylesheet2"/>
        <p:input name="data" href="oxf:/ops/unit-tests/cache/document1.xml"/>
        <p:input name="my-input" href="#b-2"/>
        <p:input name="my-unused-input" href="#c-2"/>
        <p:output name="data" ref="data"/>
    </p:processor>
</p:config>