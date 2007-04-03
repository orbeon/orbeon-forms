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
    This pipeline handles requests forwarded 
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

    <!-- Call up standard Orbeon Forms epilogue -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="/config/epilogue.xpl"/>
        <p:input name="data" href="#extracted-document"/>
        <!-- There is no "current submission" -->
        <p:input name="instance"><null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/></p:input>
        <!-- Make legacy XForms engine happy -->
        <p:input name="xforms-model"><null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/></p:input>
    </p:processor>
</p:config>
