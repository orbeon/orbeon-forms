<!--
    Copyright (C) 2012 Orbeon, Inc.

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
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <p:param name="dirty-html" type="input"/>
    <p:param name="clean-html" type="output"/>

    <p:processor name="oxf:tag-soup">
        <p:input name="data" href="#dirty-html"/>
        <p:output name="data" id="dirty-html-parsed"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#dirty-html-parsed"/>
        <p:input name="config" href="clean-html.xsl"/>
        <p:output name="data" id="clean-html-parsed"/>
    </p:processor>

    <p:processor name="oxf:html-converter">
        <p:input name="config"><config/></p:input>
        <!-- Get elements under <dummy-root>, the wrapper when going through TagSoup -->
        <p:input name="data" href="#clean-html-parsed#xpointer(/dummy-root/*)"/>
        <p:output name="data" ref="clean-html"/>
    </p:processor>

</p:config>