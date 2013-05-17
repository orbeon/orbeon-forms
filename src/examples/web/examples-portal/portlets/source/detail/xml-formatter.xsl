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
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:include href="oxf:/config/theme/xml-formatting.xsl"/>
    <xsl:template match="/">
        <html xmlns="http://www.w3.org/1999/xhtml">
            <head>
                <title>PresentationServer XML Formatter</title>
                <script>
                    <![CDATA[
                    function hideShow(obj) {
                        if (parseInt(navigator.appVersion) >= 5 || navigator.appVersion.indexOf["MSIE 5"] != -1)
                        {
                            if (obj.style.display == "none")
                                 obj.style.display = "";
                            else
                                 obj.style.display = "none";
                        }
                    }
                    function oncl(event) {
                        var target = event == null ? window.event.srcElement : event.target;

                        // Only hide/show when clicked on -/+
                        if (target.className != 'x' && target.parentNode.className != 'x') {
                            return null;
                        }

                        while (target.className != 'cd' && target.parentNode) {
                            target = target.parentNode;
                        }

                        if (target.className == 'cd') {
                            // Toggle all internal DIVs
                            var child = target.firstChild;
                            while (child) {
                                if (child.className == 'rd' || child.className == 'cd' || child.className == 'id' || child.className == 'c') {
                                    // Toggle visibility of all relevant children
                                    hideShow(child);
                                } else if (child.className == 'x' && child.firstChild) {
                                    // Toggle +/-
                                    var textNode = child.firstChild;
                                    var value = textNode.nodeValue;
                                    if (value.indexOf('-') != -1)
                                        textNode.nodeValue = value.substring(0, value.indexOf('-')) + '+' + value.substring(value.indexOf('-') + 1);
                                    else
                                        textNode.nodeValue = value.substring(0, value.indexOf('+')) + '-' + value.substring(value.indexOf('+') + 1);
                                }

                                child = child.nextSibling;
                            }
                        }
                    }
                    function initialize() {
                        document.onclick = oncl;
                    }
                    ]]>
                </script>
                <style>
                    BODY {font-family: Lucida Console, monospace; font-size: 9pt; line-height: 1.2em}
                    .cd {margin-left: 0px; margin-top: 0px; margin-bottom: 0px}
                    .rd {margin-left: 0px; margin-top: 0px; margin-bottom: 0px}
                    .id {margin-left: 2em; margin-top: 0px; margin-bottom: 0px}
                    .x {cursor: pointer; cursor:hand}
                    .c {}
                    .t {}
                </style>
            </head>
            <body onload="initialize()">
                <div class="source">
                    <xsl:apply-templates mode="xml-formatting"/>
                </div>
            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>
