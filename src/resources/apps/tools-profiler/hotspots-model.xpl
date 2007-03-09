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

    <p:param type="input" name="instance" debug="instance"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:url-generator">
        <p:input name="config" href="#instance"/>
        <p:output name="data" id="hprof-text"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="config" href="prof-to-xml.xsl"/>
        <p:input name="data" href="#hprof-text"/>
        <p:output name="data" id="hprof-xml"/>
    </p:processor>
    
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="config" href="hotspots-analysis.xsl"/>
        <p:input name="data" href="#hprof-xml"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
