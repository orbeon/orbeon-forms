<!--
    Copyright 2004 Orbeon, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <!-- Return LDAP attributes for this user, in partiular his/her common name, so we can display it -->
    <p:param name="auth-info" type="output"/>

    <p:choose href="../ldap-config.xml">
        <p:when test="not(/*/host)">
            <!-- LDAP not configured, return static data without a common name ("cn" attribute) -->
            <p:processor name="oxf:identity">
                <p:input name="data">
                    <results>
                        <result>
                            <name>uid=user,ou=People</name>
                            <attribute>
                                <name>uid</name>
                                <value>user</value>
                            </attribute>
                        </result>
                    </results>
                </p:input>
                <p:output name="data" ref="auth-info"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- LDAP configured -->
            
            <!-- Try to extract the authentication information from the session -->
            <p:processor name="oxf:session-generator">
                <p:input name="config"><key>auth</key></p:input>
                <p:output name="data" id="session"/>
            </p:processor>

            <p:choose href="#session">
                <p:when test="not(/auth/user-id != '')">
                    <!-- Information not found, retrieve it -->

                    <!-- Find current user name -->
                    <p:processor name="oxf:request-security">
                        <p:input name="config">
                            <config>
                                <role>demo-user</role>
                            </config>
                        </p:input>
                        <p:output name="data" id="request-security"/>
                    </p:processor>

                    <!-- Build LDAP query -->
                    <p:processor name="oxf:xslt">
                        <p:input name="data" href="#request-security"/>
                        <p:input name="config">
                            <xsl:stylesheet version="1.0">
                                <xsl:template match="/">
                                    <filter>
                                        <xsl:text>(uid=</xsl:text>
                                        <xsl:value-of select="/request-security/remote-user"/>
                                        <xsl:text>)</xsl:text>
                                    </filter>
                                </xsl:template>
                            </xsl:stylesheet>
                        </p:input>
                        <p:output name="data" id="ldap-filter-1"/>
                    </p:processor>

                    <!-- Run LDAP query -->
                    <p:processor name="oxf:ldap">
                        <p:input name="config" href="../ldap-config.xml"/>
                        <p:input name="filter" href="#ldap-filter-1"/>
                        <p:output name="data" id="auth"/>
                    </p:processor>

                    <!-- Save result to session -->
                    <p:processor name="oxf:session-serializer">
                        <p:input name="data" href="#auth"/>
                    </p:processor>

                    <!-- Return the result -->
                    <p:processor name="oxf:identity">
                        <p:input name="data" href="#auth"/>
                        <p:output name="data" ref="auth-info"/>
                    </p:processor>

                </p:when>
                <p:otherwise>
                    <!-- Information found, just return it -->
                    <p:processor name="oxf:identity">
                        <p:input name="data" href="#session"/>
                        <p:output name="data" ref="auth-info"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>
        </p:otherwise>
    </p:choose>
    
</p:config>
