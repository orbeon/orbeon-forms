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
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xdb="http://orbeon.org/oxf/xml/xmldb"
          xmlns:xu="http://www.xmldb.org/xupdate">

    <p:param name="document-id" type="input" schema-href="../document-id.rng"/>

    <p:choose href="../config.xml">
        <p:when test="/*/method = 'exist'">
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="../exist/delete-document.xpl"/>
                <p:input name="document-id" href="#document-id"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="../sql/delete-document.xpl"/>
                <p:input name="document-id" href="#document-id"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
