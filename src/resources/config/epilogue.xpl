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
<!--
    The epilogue is run after all page views. It is typically used to perform tasks that need to be
    done for all views, for example running the server-side XForms engine, applying a common theme,
    serializing the pages to HTML or XML, etc.
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

    <!-- The document produced by the page view -->
    <p:param type="input" name="data"/>
    <!-- The document produced by the page model -->
    <p:param type="input" name="model-data"/>
    <!-- The XML submission if any -->
    <p:param type="input" name="instance"/>
    <!-- The legacy XForms model as produced by the PFC's page/@xforms attribute if any -->
    <p:param type="input" name="xforms-model"/>

    <!-- Run the XForms epilogue -->
    <!-- If you don't use XForms at all, you can bypass this -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="/ops/pfc/xforms-epilogue.xpl"/>
        <p:input name="data" href="#data"/>
        <p:input name="model-data" href="#model-data"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="xforms-model" href="#xforms-model"/>
        <p:output name="xformed-data" id="xformed-data"/>
    </p:processor>

    <!-- Get container type information -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/container-type</include>
            </config>
        </p:input>
        <p:output name="data" id="container-type"/>
    </p:processor>

    <!-- Choose which epilogue to call depending on container type -->
    <!-- If you don't use portlets at all, you can bypass this -->
    <p:choose  href="#container-type">
        <!-- If the container is a servlet, call the servlet epilogue pipeline -->
        <p:when test="/request/container-type = 'servlet'">
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="epilogue-servlet.xpl"/>
                <p:input name="data" href="#data"/>
                <p:input name="xformed-data" href="#xformed-data"/>
                <p:input name="instance" href="#instance"/>
            </p:processor>
        </p:when>
        <!-- If the container is a portlet, call the portlet epilogue pipeline -->
        <p:otherwise>
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="epilogue-portlet.xpl"/>
                <p:input name="data" href="#data"/>
                <p:input name="xformed-data" href="#xformed-data"/>
                <p:input name="instance" href="#instance"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
