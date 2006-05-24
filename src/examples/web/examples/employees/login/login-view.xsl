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
<html xsl:version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
      xmlns:xforms="http://www.w3.org/2002/xforms"
      xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
      xmlns:xi="http://www.w3.org/2003/XInclude"
      xmlns="http://www.w3.org/1999/xhtml">
    <head>
        <title>Login</title>
        <tabs xmlns="http://orbeon.org/oxf/xml/formatting">
            <tab label="Home" selected="false" href="/employees/"/>
            <tab label="Employees" selected="false"/>
            <tab label="Reports" selected="false"/>
        </tabs>
    </head>
    <body>
        <div class="maincontent">
            <p>
                Please enter your login and password:
            </p>
            <form action="/j_security_check">
                <table>
                    <tr><td align="right">Login:</td><td><input name="j_username"/></td></tr>
                    <tr><td align="right">Password:</td><td><input type="password" name="j_password"/></td></tr>
                    <tr><td/><td><input type="submit" value="Login"/></td>
                    </tr>
                </table>
            </form>
        </div>
    </body>
</html>
