<!--
    Copyright (C) 2007 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<!--
    This pipeline handles requests to process documents, forwarded from another web application.

    It handles:

    * XHTML+XForms (which is sent to the standard epilogue)
    * XPL files with no output and no input
    * XPL files with one "data" output
    * Any other XML file (which is sent to the standard epilogue)

    Future enhancements:

    * Handle HTML tag soup input
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <!-- Extract XForms document from request -->
    <p:processor name="oxf:scope-generator">
        <p:input name="config">
            <config>
                <key>oxf.xforms.renderer.document</key>
                <scope>request</scope>
            </config>
        </p:input>
        <p:output name="data" id="extracted-document"/>
    </p:processor>

    <p:choose href="#extracted-document">
        <p:when test="/p:config and /p:config/p:param[@type = 'output' and @name = 'data']">
            <!-- Pipeline with a data output -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="#extracted-document"/>
                <p:output name="data" id="pipeline-result"/>
            </p:processor>
            <!-- Call up standard Orbeon Forms epilogue -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="/config/epilogue.xpl"/>
                <p:input name="data" href="#pipeline-result"/>
                <!-- There is no "current submission" -->
                <p:input name="instance"><null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/></p:input>
                <!-- Make legacy XForms engine happy -->
                <p:input name="xforms-model"><null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/></p:input>
            </p:processor>
        </p:when>
        <p:when test="/p:config">
            <!-- Pipeline without a data output -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="#extracted-document"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Run XInclude as that may be desirable (e.g. to include components) -->
            <p:processor name="oxf:xinclude">
                <p:input name="config" href="#extracted-document"/>
                <p:output name="data" id="xincluded-document"/>
            </p:processor>
            <!-- Call up standard Orbeon Forms epilogue -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="/config/epilogue.xpl"/>
                <p:input name="data" href="#xincluded-document"/>
                <!-- There is no "current submission" -->
                <p:input name="instance"><null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/></p:input>
                <!-- Make legacy XForms engine happy -->
                <p:input name="xforms-model"><null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/></p:input>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
