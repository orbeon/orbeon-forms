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
    <xsl:template match="/">
        <html>
            <head>
                <style type="text/css">
                    .petstore { font-family: Helvetica, Arial, sans-serif; font-size: small; }
                    .petstore_title { font-family: Helvetica, Arial, sans-serif; font-weight: bold; }
                    .petstore_footer { font-family: Helvetica, Arial, sans-serif; font-size: x-small; }
                    .petstore_listing { font-family: Helvetica, Arial, sans-serif; font-size: x-small; }
                    .petstore_form { font-family: Helvetica, Arial, sans-serif; font-size: x-small; }
                </style>
                <title><xsl:copy-of select="/root/title/node()"/></title>
            </head>
            <body bgcolor="#FFFFFF">
                <table width="100%" border="0" cellpadding="5" cellspacing="0">
                    <tr>
                        <td colspan="2"><xsl:copy-of select="/root/banner/*"/></td>
                    </tr>
                    <tr>
                        <td width="20%" valign="top"><xsl:copy-of select="/root/sidebar/*"/></td>
                        <td width="60%" valign="top"><xsl:copy-of select="/root/body"/></td>
                        <td valign="top"><!-- TODO: [mylist] --></td>
                    </tr>
                    <tr><td colspan="2"><!-- TODO: [advice banner] --></td></tr>
                    <tr><td colspan="2"><xsl:copy-of select="/root/footer/*"/></td></tr>
                </table>
            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>
