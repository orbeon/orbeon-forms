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

    <p:param name="data" type="output"/>

    <!-- Get data from the "Reports" example -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="oxf:/examples/reports/model.xpl"/>
        <p:output name="data" id="model-output"/>
    </p:processor>

    <!-- Convert the data into XSL:FO -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="oxf:/examples/reports/pdf.xpl"/>
        <p:input name="data" href="#model-output"/>
        <p:output name="data" id="xsl-fo-data"/>
    </p:processor>

    <!-- Convert the XSL:FO data into a binary document -->
    <p:processor name="oxf:xslfo-converter">
        <p:input name="config">
            <config/>
        </p:input>
        <p:input name="data" href="#xsl-fo-data"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
