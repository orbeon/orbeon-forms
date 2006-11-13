<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:exslt="http://exslt.org/common"
    xmlns:xslt="http://xml.apache.org/xslt"
    exclude-result-prefixes="exslt xslt">

    <!-- Target can be: devel, war, install -->
    <xsl:param name="target"/>
    <xsl:param name="build-root"/>
    <xsl:param name="version-number"/>

    <xsl:output method="xml" indent="yes" xslt:indent-amount="4"
        doctype-public="-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN"
        doctype-system="http://java.sun.com/dtd/web-app_2_3.dtd"/>

    <xsl:template match="/">
        <web-app>
            <display-name>Orbeon Forms <xsl:value-of select="$version-number"/></display-name>
            <description>
                Orbeon Forms is an open source forms solution that handles the complexity of forms typical of the
                enterprise or government. It is brought to standard web browsers (including Internet Explorer, Firefox,
                Safari and Opera) thanks to Ajax technology, with no need for client-side software or plugins. Orbeon
                Forms allows you to build fully interactive forms with features that include as-you-type validation,
                optional and repeated sections, always up-to-date error summaries, PDF output, full
                internationalization, and controls like auto-completion, tabs, dialogs, trees and menus.
            </description>
            <xsl:comment> Initialize main resource manager </xsl:comment>
            <context-param>
                <param-name>oxf.resources.factory</param-name>
                <param-value>org.orbeon.oxf.resources.PriorityResourceManagerFactory</param-value>
            </context-param>
            
            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'filesystem resource manager'"/>
                <xsl:with-param name="commented" select="$target != 'devel'"/>
                <xsl:with-param name="content">
                    <xsl:comment> Filesystem resource managers </xsl:comment>
                    <context-param>
                        <param-name>oxf.resources.priority.1</param-name>
                        <param-value>org.orbeon.oxf.resources.FilesystemResourceManagerFactory</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.resources.priority.1.oxf.resources.filesystem.sandbox-directory</param-name>
                        <param-value><xsl:value-of select="$build-root"/>/src/resources</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.resources.priority.2</param-name>
                        <param-value>org.orbeon.oxf.resources.FilesystemResourceManagerFactory</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.resources.priority.2.oxf.resources.filesystem.sandbox-directory</param-name>
                        <param-value><xsl:value-of select="$build-root"/>/src/resources-packaged</param-value>
                    </context-param>
                    <xsl:comment> Classloader resource manager </xsl:comment>
                    <context-param>
                        <param-name>oxf.resources.priority.3</param-name>
                        <param-value>org.orbeon.oxf.resources.ClassLoaderResourceManagerFactory</param-value>
                    </context-param>
                </xsl:with-param>
            </xsl:call-template>
            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'webapp resource manager'"/>
                <xsl:with-param name="commented" select="$target = 'devel'"/>
                <xsl:with-param name="content">
                    <xsl:comment> Web application resource manager </xsl:comment>
                    <context-param>
                        <param-name>oxf.resources.priority.1</param-name>
                        <param-value>org.orbeon.oxf.resources.WebAppResourceManagerFactory</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.resources.priority.1.oxf.resources.webapp.rootdir</param-name>
                        <param-value>/WEB-INF/resources</param-value>
                    </context-param>
                    <xsl:comment> Classloader resource manager </xsl:comment>
                    <context-param>
                        <param-name>oxf.resources.priority.2</param-name>
                        <param-value>org.orbeon.oxf.resources.ClassLoaderResourceManagerFactory</param-value>
                    </context-param>
                </xsl:with-param>
            </xsl:call-template>

            <xsl:comment> OPS Class Loader </xsl:comment>
            <context-param>
                <param-name>oxf.classloader.enable</param-name>
                <param-value>false</param-value>
            </context-param>
            <context-param>
                <param-name>oxf.classloader.ignore-packages</param-name>
                <param-value>java. javax. org.apache.log4j. org.xml. org.w3c.</param-value>
            </context-param>

            <xsl:comment> Set location of properties.xml (read by resource manager) </xsl:comment>
            <context-param>
                <param-name>oxf.properties</param-name>
                <param-value>oxf:/config/properties.xml</param-value>
            </context-param>

            <xsl:comment> Determine whether logging initialization must take place </xsl:comment>
            <context-param>
               <param-name>oxf.initialize-logging</param-name>
               <param-value>true</param-value>
            </context-param>

            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'Set XML Server configuration file'"/>
                <xsl:with-param name="commented" select="true()"/>
                <xsl:with-param name="content">
                    <context-param>
                        <param-name>oxf.xml-server.config</param-name>
                        <param-value>oxf:/ops/xml-server/xml-server.xml</param-value>
                    </context-param>
                </xsl:with-param>
            </xsl:call-template>

            <xsl:comment> Set context listener processors </xsl:comment>
            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'context listener processors'"/>
                <xsl:with-param name="commented" select="$target = 'war'"/>
                <xsl:with-param name="content">
                    <context-param>
                        <param-name>oxf.context-initialized-processor.name</param-name>
                        <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.context-initialized-processor.input.config</param-name>
                        <param-value>oxf:/apps/context/context-initialized.xpl</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.context-destroyed-processor.name</param-name>
                        <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.context-destroyed-processor.input.config</param-name>
                        <param-value>oxf:/apps/context/context-destroyed.xpl</param-value>
                    </context-param>
                </xsl:with-param>
            </xsl:call-template>

            <xsl:comment> Set session listener processors </xsl:comment>
            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'session listener processors'"/>
                <xsl:with-param name="commented" select="$target = 'war'"/>
                <xsl:with-param name="content">
                    <context-param>
                        <param-name>oxf.session-created-processor.name</param-name>
                        <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.session-created-processor.input.config</param-name>
                        <param-value>oxf:/apps/context/session-created.xpl</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.session-destroyed-processor.name</param-name>
                        <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.session-destroyed-processor.input.config</param-name>
                        <param-value>oxf:/apps/context/session-destroyed.xpl</param-value>
                    </context-param>
                </xsl:with-param>
            </xsl:call-template>

            <xsl:comment> All JSP files under /xforms-jsp go through the OPS filter </xsl:comment>
            <filter>
                <filter-name>ops-main-filter</filter-name>
                <filter-class>org.orbeon.oxf.servlet.OPSServletFilter</filter-class>
                <init-param>
                    <param-name>oxf.main-processor.name</param-name>
                    <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                </init-param>
                <init-param>
                    <param-name>oxf.main-processor.input.config</param-name>
                    <param-value>oxf:/config/filter.xpl</param-value>
                </init-param>
                <init-param>
                    <param-name>oxf.error-processor.name</param-name>
                    <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                </init-param>
                <init-param>
                    <param-name>oxf.error-processor.input.config</param-name>
                    <param-value>oxf:/config/error.xpl</param-value>
                </init-param>
            </filter>
            <filter-mapping>
                <filter-name>ops-main-filter</filter-name>
                <url-pattern>/xforms-jsp/*</url-pattern>
            </filter-mapping>

            <xsl:comment> Set listeners </xsl:comment>
            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'listeners'"/>
                <xsl:with-param name="commented" select="$target = 'war'"/>
                <xsl:with-param name="content">
                    <listener>
                        <listener-class>org.orbeon.oxf.webapp.OPSServletContextListener</listener-class>
                    </listener>
                    <listener>
                        <listener-class>org.orbeon.oxf.webapp.OPSSessionListener</listener-class>
                    </listener>
                </xsl:with-param>
            </xsl:call-template>

            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'XML Server'"/>
                <xsl:with-param name="commented" select="true()"/>
                <xsl:with-param name="content">
                    <listener>
                        <listener-class>org.orbeon.oxf.xmlserver.ContextListener</listener-class>
                    </listener>
                </xsl:with-param>
            </xsl:call-template>

            <!--
            <servlet>
                <servlet-name>ops-xhtml-xforms-servlet</servlet-name>
                <servlet-class>org.orbeon.oxf.servlet.OPSServlet</servlet-class>
                <init-param>
                    <param-name>oxf.main-processor.name</param-name>
                    <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                </init-param>
                <init-param>
                    <param-name>oxf.main-processor.input.config</param-name>
                    <param-value>oxf:/config/run-xforms.xpl</param-value>
                </init-param>
                <init-param>
                    <param-name>oxf.error-processor.name</param-name>
                    <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                </init-param>
                <init-param>
                    <param-name>oxf.error-processor.input.config</param-name>
                    <param-value>oxf:/config/error.xpl</param-value>
                </init-param>
                <load-on-startup>1</load-on-startup>
            </servlet>

            <servlet-mapping>
                <servlet-name>ops-xhtml-xforms-servlet</servlet-name>
                <url-pattern>/test/*</url-pattern>
            </servlet-mapping>
            -->

            <servlet>
                <servlet-name>ops-main-servlet</servlet-name>
                <servlet-class>org.orbeon.oxf.servlet.OPSServlet</servlet-class>
                <xsl:comment> Set main processor </xsl:comment>
                <init-param>
                    <param-name>oxf.main-processor.name</param-name>
                    <param-value>{http://www.orbeon.com/oxf/processors}page-flow</param-value>
                </init-param>
                <init-param>
                    <param-name>oxf.main-processor.input.controller</param-name>
                    <param-value>oxf:/page-flow.xml</param-value>
                </init-param>
                <xsl:comment> Set error processor </xsl:comment>
                <init-param>
                    <param-name>oxf.error-processor.name</param-name>
                    <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                </init-param>
                <init-param>
                    <param-name>oxf.error-processor.input.config</param-name>
                    <param-value>oxf:/config/error.xpl</param-value>
                </init-param>
                <xsl:comment> Set initialization listener </xsl:comment>
                <init-param>
                    <param-name>oxf.servlet-initialized-processor.name</param-name>
                    <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                </init-param>
                <init-param>
                    <param-name>oxf.servlet-initialized-processor.input.config</param-name>
                    <param-value>oxf:/apps/context/servlet-initialized.xpl</param-value>
                </init-param>
                <xsl:comment> Set destruction listener </xsl:comment>
                <init-param>
                    <param-name>oxf.servlet-destroyed-processor.name</param-name>
                    <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                </init-param>
                <init-param>
                    <param-name>oxf.servlet-destroyed-processor.input.config</param-name>
                    <param-value>oxf:/apps/context/servlet-destroyed.xpl</param-value>
                </init-param>
                <load-on-startup>1</load-on-startup>
            </servlet>

            <servlet>
                <servlet-name>ops-xforms-server-servlet</servlet-name>
                <servlet-class>org.orbeon.oxf.servlet.OPSServlet</servlet-class>
                <xsl:comment> Set main processor </xsl:comment>
                <init-param>
                    <param-name>oxf.main-processor.name</param-name>
                    <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                </init-param>
                <init-param>
                    <param-name>oxf.main-processor.input.config</param-name>
                    <param-value>oxf:/ops/xforms/xforms-server.xpl</param-value>
                </init-param>
                <xsl:comment> Set error processor </xsl:comment>
                <init-param>
                    <param-name>oxf.error-processor.name</param-name>
                    <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                </init-param>
                <init-param>
                    <param-name>oxf.error-processor.input.config</param-name>
                    <param-value>oxf:/ops/xforms/xforms-server-error.xpl</param-value>
                </init-param>
                <load-on-startup>1</load-on-startup>
            </servlet>

            <servlet>
                <servlet-name>display-chart-servlet</servlet-name>
                <servlet-class>org.jfree.chart.servlet.DisplayChart</servlet-class>
                <load-on-startup>2</load-on-startup>
            </servlet>

            <servlet>
                <servlet-name>exist-rest-servlet</servlet-name>
                <servlet-class>org.exist.http.servlets.EXistServlet</servlet-class>
                <init-param>
                    <param-name>basedir</param-name>
                    <param-value>WEB-INF/</param-value>
                </init-param>
                 <init-param>
                    <param-name>configuration</param-name>
                    <param-value>exist-conf.xml</param-value>
                </init-param>
                <init-param>
                    <param-name>start</param-name>
                    <param-value>true</param-value>
                </init-param>
                <load-on-startup>2</load-on-startup>
            </servlet>

            <servlet>
                <servlet-name>exist-webdav-servlet</servlet-name>
                <servlet-class>org.exist.http.servlets.WebDAVServlet</servlet-class>
                <init-param>
                    <param-name>authentication</param-name>
                    <param-value>basic</param-value>
                </init-param>
            </servlet>

            <servlet>
                <servlet-name>struts-servlet</servlet-name>
                <servlet-class>org.apache.struts.action.ActionServlet</servlet-class>
                <init-param>
                    <param-name>config</param-name>
                    <param-value>/WEB-INF/struts-config.xml</param-value>
                </init-param>
                <init-param>
                    <param-name>config/examples/struts/module</param-name>
                    <param-value>/WEB-INF/struts-module.xml</param-value>
                </init-param>
                <load-on-startup>3</load-on-startup>
            </servlet>

            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'SQL examples'"/>
                <xsl:with-param name="commented" select="$target = 'war'"/>
                <xsl:with-param name="content">
                    <servlet>
                        <servlet-name>hsqldb-servlet</servlet-name>
                        <servlet-class>org.hsqldb.Servlet</servlet-class>
                        <init-param>
                            <param-name>hsqldb.server.database</param-name>
                            <param-value>orbeondb</param-value>
                        </init-param>
                        <load-on-startup>4</load-on-startup>
                    </servlet>
                </xsl:with-param>
            </xsl:call-template>

            <servlet-mapping>
                <servlet-name>ops-main-servlet</servlet-name>
                <url-pattern>/</url-pattern>
            </servlet-mapping>

            <servlet-mapping>
                <servlet-name>ops-xforms-server-servlet</servlet-name>
                <url-pattern>/xforms-server</url-pattern>
            </servlet-mapping>

            <servlet-mapping>
                <servlet-name>exist-rest-servlet</servlet-name>
                <url-pattern>/exist/rest/*</url-pattern>
            </servlet-mapping>

            <servlet-mapping>
                <servlet-name>exist-webdav-servlet</servlet-name>
                <url-pattern>/exist/webdav/*</url-pattern>
            </servlet-mapping>

            <servlet-mapping>
                <servlet-name>struts-servlet</servlet-name>
                <url-pattern>/struts/*</url-pattern>
            </servlet-mapping>

            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'SQL examples'"/>
                <xsl:with-param name="commented" select="$target = 'war'"/>
                <xsl:with-param name="content">
                    <servlet-mapping>
                        <servlet-name>hsqldb-servlet</servlet-name>
                        <url-pattern>/db</url-pattern>
                    </servlet-mapping>
                </xsl:with-param>
            </xsl:call-template>

            <servlet-mapping>
                <servlet-name>display-chart-servlet</servlet-name>
                <url-pattern>/chartDisplay</url-pattern>
            </servlet-mapping>

            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'SQL examples'"/>
                <xsl:with-param name="commented" select="$target = 'war'"/>
                <xsl:with-param name="content">
                    <resource-ref>
                        <description>DataSource</description>
                        <res-ref-name>jdbc/db</res-ref-name>
                        <res-type>javax.sql.DataSource</res-type>
                        <res-auth>Container</res-auth>
                    </resource-ref>
                </xsl:with-param>
            </xsl:call-template>

            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'authentication example'"/>
                <xsl:with-param name="commented" select="$target = 'war'"/>
                <xsl:with-param name="content">
                    <security-constraint>
                        <web-resource-collection>
                            <web-resource-name>Administration</web-resource-name>
                            <url-pattern>/examples-standalone/authentication</url-pattern>
                        </web-resource-collection>
                        <auth-constraint>
                            <role-name>administrator</role-name>
                        </auth-constraint>
                    </security-constraint>
                    <login-config>
                        <auth-method>FORM</auth-method>
                        <form-login-config>
                            <form-login-page>/examples-standalone/authentication/login</form-login-page>
                            <form-error-page>/examples-standalone/authentication/login-error</form-error-page>
                        </form-login-config>
                    </login-config>
                    <security-role>
                        <role-name>administrator</role-name>
                    </security-role>
                </xsl:with-param>
            </xsl:call-template>
        </web-app>
    </xsl:template>

    <xsl:template name="comment">
        <xsl:param name="caption"/>
        <xsl:param name="commented"/>
        <xsl:param name="content"/>
        <xsl:comment>
            <xsl:text> Uncomment this for the </xsl:text>
            <xsl:value-of select="$caption"/>
            <xsl:text> </xsl:text>
        </xsl:comment>
        <xsl:choose>
            <xsl:when test="$commented">
                <xsl:comment>
                    <xsl:for-each select="exslt:node-set($content)/*">
                        <xsl:call-template name="to-text">
                            <xsl:with-param name="node" select="."/>
                        </xsl:call-template>
                    </xsl:for-each>
                </xsl:comment>
            </xsl:when>
            <xsl:otherwise>
                <xsl:copy-of select="exslt:node-set($content)/*"/>
            </xsl:otherwise>
        </xsl:choose>
        <xsl:comment>
            <xsl:text> End </xsl:text>
            <xsl:value-of select="$caption"/>
            <xsl:text> </xsl:text>
        </xsl:comment>
    </xsl:template>

    <xsl:template name="to-text">
        <xsl:param name="node"/>
        <xsl:param name="level" select="1"/>
        <xsl:choose>
            <xsl:when test="name($node) != ''">
                <xsl:text>&#10;</xsl:text>
                <xsl:call-template name="indent">
                    <xsl:with-param name="level" select="$level"/>
                </xsl:call-template>
                <xsl:text>&lt;</xsl:text>
                <xsl:value-of select="name($node)"/>
                <xsl:text>&gt;</xsl:text>
                <xsl:for-each select="$node/node()">
                    <xsl:call-template name="to-text">
                        <xsl:with-param name="node" select="."/>
                        <xsl:with-param name="level" select="$level + 1"/>
                    </xsl:call-template>
                </xsl:for-each>
                <xsl:if test="count($node/*) > 0">
                    <xsl:text>&#10;</xsl:text>
                    <xsl:call-template name="indent">
                        <xsl:with-param name="level" select="$level"/>
                    </xsl:call-template>
                </xsl:if>
                <xsl:text>&lt;/</xsl:text>
                <xsl:value-of select="name($node)"/>
                <xsl:text>&gt;</xsl:text>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$node"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="indent">
        <xsl:param name="level"/>
        <xsl:if test="$level > 0">
            <xsl:text>    </xsl:text>
            <xsl:call-template name="indent">
                <xsl:with-param name="level" select="$level - 1"/>
            </xsl:call-template>
        </xsl:if>
    </xsl:template>


</xsl:stylesheet>
