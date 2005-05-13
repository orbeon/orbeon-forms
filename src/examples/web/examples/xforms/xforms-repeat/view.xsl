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
<xhtml:html xsl:version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
        xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml">
    <xhtml:head><xhtml:title>XForms Repeating Elements</xhtml:title></xhtml:head>
    <xhtml:body>
        <xforms:group ref="form">
            <table border="0" cellpadding="10" cellspacing="0">
                <tr>
                    <td colspan="2">
                        <xforms:repeat nodeset="department" id="departmentSet">
                            <div class="bodytd" style="margin-bottom: 1em; padding-left: 1em">
                                <p>
                                    <i>Department:</i>
                                    <xforms:input ref="@name" xhtml:size="10"/>
                                    <xforms:submit>
                                        <xforms:label>X</xforms:label>
                                        <xforms:delete nodeset="/form/department" at="index('departmentSet')"/>
                                    </xforms:submit>
                                </p>
                                <xforms:repeat nodeset="employee" id="employeeSet">
                                    <p style="margin-left: 2em; margin: 0.5em; white-space: nowrap">
                                        <i> Employee:</i>
                                        <xforms:input ref="@firstname" xhtml:size="10"/>
                                        <xforms:input ref="@lastname" xhtml:size="10"/>
                                        <xforms:submit>
                                            <xforms:label>X</xforms:label>
                                            <xforms:delete nodeset="/form/department[index('departmentSet')]/employee" at="index('employeeSet')"/>
                                        </xforms:submit>
                                    </p>
                                </xforms:repeat>
                                <p style="margin-left: 2em; margin: 0.5em; white-space: nowrap">
                                    <xforms:submit>
                                        <xforms:label>Add employee</xforms:label>
                                        <xforms:insert nodeset="/form/department[index('departmentSet')]/employee" at="last()" position="after"/>
                                    </xforms:submit>
                                </p>
                            </div>
                        </xforms:repeat>
                        <p>
                            <xforms:submit>
                                <xforms:label>Add department</xforms:label>
                                <xforms:insert nodeset="/form/department" at="last()" position="after"/>
                            </xforms:submit>
                            <xforms:submit>
                                <xforms:label>Update instance</xforms:label>
                            </xforms:submit>
                        </p>
                    </td>
                </tr>
                <tr>
                    <td align="right">XForms instance:</td>
                    <td>
                        <f:xml-source>
                            <xsl:copy-of select="doc('input:instance')/*"/>
                        </f:xml-source>
                    </td>
                </tr>
            </table>
        </xforms:group>
    </xhtml:body>
</xhtml:html>
