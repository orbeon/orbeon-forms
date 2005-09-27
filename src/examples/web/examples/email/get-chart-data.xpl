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

    <!-- Get data from the "Charts" example -->
    <p:processor name="oxf:chart-converter">
        <p:input name="config"><config/></p:input>
        <p:input name="data" href="oxf:/examples/charts/default-submission.xml"/>
        <p:input name="chart" href="oxf:/examples/charts/default-submission.xml#xpointer(/form/chart)"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
