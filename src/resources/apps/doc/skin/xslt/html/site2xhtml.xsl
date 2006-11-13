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
    <xsl:template match="site">
        <html>
            <head>
                <title>
                    <xsl:value-of select="div[@class='content']/div[@class = 'title']/h1"/>
                </title>

                <style>
                    .cd {margin-left: 0px; margin-top: 0px; margin-bottom: 0px}
                    .rd {margin-left: 0px; margin-top: 0px; margin-bottom: 0px}
                    .id {margin-left: 2em; margin-top: 0px; margin-bottom: 0px}
                    .x {cursor: pointer; cursor:hand}
                    .c {}
                    .t {}
                </style>
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
            </head>
            <body bgcolor="#FFFFFF" text="#000000" leftmargin="0" topmargin="0" marginwidth="0" marginheight="0" onload="initialize()">
                <link rel="stylesheet" href="/doc/skin/css/page.css" type="text/css"/>
                <link rel="stylesheet" href="skin/css/page.css" type="text/css"/>
                <table cellspacing="0" cellpadding="0" border="0" width="100%" bgcolor="#ffffff" summary="page content">
                    <tr>
                        <td valign="top">
                            <table cellpadding="0" cellspacing="0" border="0" summary="menu">
                                <tr>
                                    <td valign="top" rowspan="3">
                                        <table cellspacing="0" cellpadding="0" border="0" summary="blue line">
                                            <tr>
                                                <td bgcolor="#294563">
                                                    <img src="/doc/skin/images/spacer.gif" alt="" height="1" width="10"/>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td bgcolor="#FF9933">
                                                    <font face="Arial, Helvetica, Sans-serif" size="4" color="#FFCC66">&#160;</font>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td bgcolor="#294563">
                                                    <img src="/doc/skin/images/spacer.gif" alt="" height="1" width="10"/>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                    <td bgcolor="#294563">
                                        <img src="/doc/skin/images/spacer.gif" alt="" height="1" width="1"/>
                                    </td>
                                    <td bgcolor="#FFCC66" valign="bottom">
                                        <img src="/doc/skin/images/spacer.gif" alt="" height="10" width="10"/>
                                    </td>
                                    <td bgcolor="#FFCC66" valign="top" nowrap="nowrap">
                                        <xsl:apply-templates select="div[@class='menu']"/>
                                    </td>
                                    <td bgcolor="#FFCC66" valign="bottom">
                                        <img src="/doc/skin/images/spacer.gif" alt="" height="10" width="10"/>
                                    </td>
                                    <td bgcolor="#294563">
                                        <img src="/doc/skin/images/spacer.gif" alt="" height="1" width="1"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td bgcolor="#294563" height="1" colspan="5">
                                        <img src="/doc/skin/images/spacer.gif" alt="" height="1" width="1"/>
                                    </td>
                                </tr>
                            </table>
                        </td>
                        <td width="100%" valign="top">
                            <table cellspacing="0" cellpadding="0" border="0" width="100%" summary="content">
                                <tr>
                                    <td bgcolor="#294563" colspan="4">
                                        <img src="/doc/skin/images/spacer.gif" alt="" height="1" width="10"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td bgcolor="#FF9933" width="10" align="left">
                                        <img src="/doc/skin/images/spacer.gif" alt="" height="25" width="1"/>
                                    </td>
                                    <td bgcolor="#FF9933" width="50%" align="right" colspan="2">
                                        <div class="content" style="margin-right: 1em">
                                            <form method="GET" style="margin-bottom: 0; margin-top: 0" action="http://www.google.com/custom">
                                                <span style="white-space: nowrap">
                                                    <a style="color: black" href="http://www.orbeon.com/">Orbeon Forms Home Page</a> |
                                                    <a style="color: black" href="/examples/">Showcase Application</a> |
                                                    <span style="white-space: nowrap">
                                                        Search:
                                                        <input TYPE="text" name="q" size="10" maxlength="255" value=""/>
                                                        <input type="submit" name="sa" VALUE="Go" style="margin-left: 0.2em;"/>
                                                    </span>
                                                </span>
                                                <input type="hidden" name="cof" VALUE="GIMP:#FF9900;T:black;LW:510;ALC:#FF9900;L:http://www.orbeon.com/images/orbeon-google.png;GFNT:#666699;LC:#666699;LH:42;BGC:#FFFFFF;AH:center;VLC:#666699;GL:0;S:http://www.orbeon.com;GALT:#FF9900;AWFID:8ac636f034abb7d8;"/>
                                                <input type="hidden" name="sitesearch" value="orbeon.com"/>
                                            </form>
                                        </div>
                                    </td>
                                </tr>
                                <tr>
                                    <td bgcolor="#294563" colspan="4">
                                        <img src="/doc/skin/images/spacer.gif" alt="" height="1" width="10"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="10" align="left">
                                        <img src="/doc/skin/images/spacer.gif" alt="" height="1" width="10"/>
                                    </td>
                                    <td width="100%" align="left">
                                        <xsl:apply-templates select="div[@class='content']"/>
                                    </td>
                                    <td width="10">
                                        <img src="/doc/skin/images/spacer.gif" alt="" height="1" width="10"/>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                <!-- footer -->
                <table border="0" height="20" width="100%" cellpadding="0" cellspacing="0" summary="footer">
                    <tr>
                        <td width="10">
                            <img src="/doc/skin/images/spacer.gif" alt="" height="1" width="10"/>
                        </td>
                    </tr>
                    <tr>
                        <td bgcolor="#FFCC66" height="1" colspan="2">
                            <img src="/doc/skin/images/spacer.gif" alt="" width="1" height="1"/>
                        </td>
                    </tr>
                    <tr>
                        <td align="center" class="copyright" bgcolor="#FF9933" colspan="2">
                            <font face="Arial, Helvetica, Sans-Serif" size="2">Copyright &#169; 2003 Orbeon, Inc. All Rights Reserved.</font>
                        </td>
                    </tr>
                </table>
            </body>
        </html>
    </xsl:template>
    <xsl:template match="node()|@*" priority="-1">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>
