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

    <!-- Initialialize database when necessary -->
    <p:processor name="oxf:java">
        <p:input name="config">
            <config sourcepath="oxf:/init-database" class="IsDatabaseIntialized"/>
        </p:input>
        <p:output name="data" id="is-initialiazed"/>
    </p:processor>

    <p:choose href="#is-initialiazed">
        <p:when test="/is-initialiazed = 'false'">
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="populate.xpl"/>
            </p:processor>
        </p:when>
    </p:choose>

</p:config>
