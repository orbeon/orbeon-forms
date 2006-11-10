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
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:processor name="oxf:xslt">
        <p:input name="data" href="../book.xml"/>
        <p:input name="config" href="aggregate.xsl"/>
        <p:output name="data" id="all-forrest"/>
    </p:processor>

    <p:processor name="oxf:xalan">
        <p:input name="data" href="#all-forrest"/>
        <p:input name="config" href="forrest-to-linuxdoc.xsl"/>
        <p:output name="data" id="linuxdoc"/>
    </p:processor>

    <p:processor name="oxf:xalan">
        <p:input name="data" href="#linuxdoc"/>
        <p:input name="config" href="sgmlize.xsl"/>
        <p:output name="data" id="sgml"/>
    </p:processor>

    <p:processor name="oxf:text-serializer">
        <p:input name="config"><config/></p:input>
        <p:input name="data" href="#sgml"/>
    </p:processor>

</p:config>
