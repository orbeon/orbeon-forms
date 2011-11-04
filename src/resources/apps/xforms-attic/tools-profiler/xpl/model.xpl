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
          xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:delegation="http://orbeon.org/oxf/xml/delegation"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="output" name="data"/>

    <p:processor name="oxf:pipeline-trace">
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
