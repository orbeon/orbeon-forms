<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:exslt="http://exslt.org/common"
    xmlns:xslt="http://xml.apache.org/xslt"
    exclude-result-prefixes="exslt xslt">

    <!-- Target can be: devel or war -->
    <xsl:param name="target"/>
    <xsl:param name="build-root"/>
    <xsl:param name="version"/>
    <xsl:param name="edition"/>

    <xsl:output method="xml" indent="yes" xslt:indent-amount="4"/>

    <xsl:template match="/">
        <web-app xmlns="http://java.sun.com/xml/ns/j2ee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 version="2.4" xsi:schemaLocation="http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd">
            <display-name>Orbeon Forms</display-name>
            <description>
        Orbeon Forms is an open source, standard-based web forms solution, which
        includes Form Builder, a WYSIWYG browser-based authoring tool, and Form
        Runner, a runtime environment which facilitates the deployment and
        integration of a large number of complex forms. Orbeon Forms implements
        different technologies, such as XForms and Ajax, with no need for
        client-side software or plug-ins.
    </description>
            <xsl:comment>Initialize main resource manager</xsl:comment>
            <context-param>
                <param-name>oxf.resources.factory</param-name>
                <param-value>org.orbeon.oxf.resources.PriorityResourceManagerFactory</param-value>
            </context-param>

            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'filesystem resource manager (development mode)'"/>
                <xsl:with-param name="commented" select="$target != 'dev'"/>
                <xsl:with-param name="content">
                    <xsl:comment>Filesystem resource managers</xsl:comment>
                    <context-param>
                        <param-name>oxf.resources.priority.0</param-name>
                        <param-value>org.orbeon.oxf.resources.FilesystemResourceManagerFactory</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.resources.priority.0.oxf.resources.filesystem.sandbox-directory</param-name>
                        <param-value><xsl:value-of select="$build-root"/>/src/resources-local</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.resources.priority.1</param-name>
                        <param-value>org.orbeon.oxf.resources.FilesystemResourceManagerFactory</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.resources.priority.1.oxf.resources.filesystem.sandbox-directory</param-name>
                        <param-value><xsl:value-of select="$build-root"/>/src/resources</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.resources.priority.3</param-name>
                        <param-value>org.orbeon.oxf.resources.FilesystemResourceManagerFactory</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.resources.priority.3.oxf.resources.filesystem.sandbox-directory</param-name>
                        <param-value><xsl:value-of select="$build-root"/>/src/resources-packaged</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.resources.priority.5</param-name>
                        <param-value>org.orbeon.oxf.resources.FilesystemResourceManagerFactory</param-value>
                    </context-param>
                    <context-param>
                        <param-name>oxf.resources.priority.5.oxf.resources.filesystem.sandbox-directory</param-name>
                        <param-value><xsl:value-of select="$build-root"/>/src/test/resources</param-value>
                    </context-param>
                </xsl:with-param>
            </xsl:call-template>
            <xsl:comment>Web application resource manager for resources</xsl:comment>
            <context-param>
                <param-name>oxf.resources.priority.2</param-name>
                <param-value>org.orbeon.oxf.resources.WebAppResourceManagerFactory</param-value>
            </context-param>
            <context-param>
                <param-name>oxf.resources.priority.2.oxf.resources.webapp.rootdir</param-name>
                <param-value>/WEB-INF/resources</param-value>
            </context-param>
            <xsl:comment>Classloader resource manager</xsl:comment>
            <context-param>
                <param-name>oxf.resources.priority.4</param-name>
                <param-value>org.orbeon.oxf.resources.ClassLoaderResourceManagerFactory</param-value>
            </context-param>
            <xsl:comment>Set run mode ("dev" or "prod")</xsl:comment>
            <context-param>
                <param-name>oxf.run-mode</param-name>
                <param-value>
                    <xsl:choose>
                        <xsl:when test="$target = 'dev'">dev</xsl:when>
                        <xsl:otherwise>prod</xsl:otherwise>
                    </xsl:choose>
                </param-value>
            </context-param>

            <xsl:comment>Set location of properties.xml</xsl:comment>
            <context-param>
                <param-name>oxf.properties</param-name>
                <param-value>oxf:/config/properties-${oxf.run-mode}.xml</param-value>
            </context-param>

            <xsl:comment>Determine whether logging initialization must take place</xsl:comment>
            <context-param>
                <param-name>oxf.initialize-logging</param-name>
                <param-value>true</param-value>
            </context-param>

            <xsl:comment>Set context listener processors</xsl:comment>
            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'context listener processors'"/>
                <xsl:with-param name="commented" select="$target != 'dev'"/>
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

            <xsl:comment>Set session listener processors</xsl:comment>
            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'session listener processors'"/>
                <xsl:with-param name="commented" select="$target != 'dev'"/>
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

            <xsl:comment>Security filter for eXist</xsl:comment>
            <filter>
                <filter-name>orbeon-exist-filter</filter-name>
                <filter-class>org.orbeon.oxf.servlet.TokenSecurityFilter</filter-class>
            </filter>
            <filter-mapping>
                <filter-name>orbeon-exist-filter</filter-name>
                <url-pattern>/exist/*</url-pattern>
                <dispatcher>REQUEST</dispatcher>
                <dispatcher>FORWARD</dispatcher>
            </filter-mapping>

            <xsl:comment>Limit concurrent access to Form Runner</xsl:comment>
            <filter>
                <filter-name>orbeon-limiter-filter</filter-name>
                <filter-class>org.orbeon.oxf.servlet.LimiterFilter</filter-class>
                <xsl:comment>Include Form Runner pages and XForms Ajax requests</xsl:comment>
                <init-param>
                    <param-name>include</param-name>
                    <param-value>(/fr/.*)|(/xforms-server)</param-value>
                </init-param>
                <xsl:comment>Exclude resources not produced by services</xsl:comment>
                <init-param>
                    <param-name>exclude</param-name>
                    <param-value>(?!/([^/]+)/service/).+\.(gif|css|pdf|json|js|coffee|map|png|jpg|xsd|htc|ico|swf|html|htm|txt)</param-value>
                </init-param>
                <xsl:comment>Minimum, requested, and maximum number of concurrent threads allowed</xsl:comment>
                <xsl:comment>The `x` prefix specifies a multiple of the number of CPU cores reported by the JVM</xsl:comment>
                <init-param>
                    <param-name>min-threads</param-name>
                    <param-value>1</param-value>
                </init-param>
                <init-param>
                    <param-name>num-threads</param-name>
                    <param-value>x1</param-value>
                </init-param>
                <init-param>
                    <param-name>max-threads</param-name>
                    <param-value>x1</param-value>
                </init-param>
            </filter>
            <filter-mapping>
                <filter-name>orbeon-limiter-filter</filter-name>
                <url-pattern>/*</url-pattern>
                <dispatcher>REQUEST</dispatcher>
            </filter-mapping>

            <xsl:comment>Add internal Orbeon-* headers for auth</xsl:comment>
            <filter>
                <filter-name>orbeon-form-runner-auth-servlet-filter</filter-name>
                <filter-class>org.orbeon.oxf.servlet.FormRunnerAuthFilter</filter-class>
            </filter>
            <filter-mapping>
                <filter-name>orbeon-form-runner-auth-servlet-filter</filter-name>
                <url-pattern>/*</url-pattern>
                <dispatcher>REQUEST</dispatcher>
                <dispatcher>FORWARD</dispatcher>
            </filter-mapping>

            <xsl:comment>All JSP files under /xforms-jsp go through the XForms filter</xsl:comment>
            <filter>
                <filter-name>orbeon-xforms-filter</filter-name>
                <filter-class>org.orbeon.oxf.servlet.OrbeonXFormsFilter</filter-class>
                <xsl:call-template name="comment">
                    <xsl:with-param name="caption" select="'separate WAR deployment'"/>
                    <xsl:with-param name="commented" select="true()"/>
                    <xsl:with-param name="content">
                        <init-param>
                            <param-name>oxf.xforms.renderer.context</param-name>
                            <param-value>/orbeon</param-value>
                        </init-param>
                        <init-param>
                            <param-name>oxf.xforms.renderer.default-encoding</param-name>
                            <param-value>UTF-8</param-value>
                        </init-param>
                    </xsl:with-param>
                </xsl:call-template>
            </filter>
            <filter-mapping>
                <filter-name>orbeon-xforms-filter</filter-name>
                <url-pattern>/xforms-jsp/*</url-pattern>
                <xsl:comment>Servlet 2.4 configuration allowing the filter to run upon forward in addition to request</xsl:comment>
                <dispatcher>REQUEST</dispatcher>
                <dispatcher>FORWARD</dispatcher>
            </filter-mapping>

            <xsl:comment>Orbeon context listener</xsl:comment>
            <listener>
                <listener-class>org.orbeon.oxf.webapp.OrbeonServletContextListener</listener-class>
            </listener>

            <!-- NOTE: The session listener is used by the XForm state store so must be enabled  -->
            <xsl:comment>Orbeon session listener</xsl:comment>
            <listener>
                <listener-class>org.orbeon.oxf.webapp.OrbeonSessionListener</listener-class>
            </listener>

            <!-- NOTE: This also helps with Tomcat shutdown -->
            <xsl:comment>Ehcache shutdown listener</xsl:comment>
            <listener>
                <listener-class>net.sf.ehcache.constructs.web.ShutdownListener</listener-class>
            </listener>

            <xsl:comment>This is the main Orbeon Forms servlet</xsl:comment>
            <servlet>
                <servlet-name>orbeon-main-servlet</servlet-name>
                <servlet-class>org.orbeon.oxf.servlet.OrbeonServlet</servlet-class>
                <xsl:comment>Set main processor</xsl:comment>
                <init-param>
                    <param-name>oxf.main-processor.name</param-name>
                    <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                </init-param>
                <init-param>
                    <param-name>oxf.main-processor.input.config</param-name>
                    <param-value>oxf:/config/prologue-servlet.xpl</param-value>
                </init-param>
                <xsl:comment>Set error processor</xsl:comment>
                <init-param>
                    <param-name>oxf.error-processor.name</param-name>
                    <param-value>{http://www.orbeon.com/oxf/processors}page-flow</param-value>
                </init-param>
                <init-param>
                    <param-name>oxf.error-processor.input.controller</param-name>
                    <param-value>oxf:/config/error-page-flow.xml</param-value>
                </init-param>
                <xsl:comment>Set supported methods</xsl:comment>
                <init-param>
                    <param-name>oxf.http.accept-methods</param-name>
                    <param-value>get,post,head,put,delete</param-value>
                </init-param>
                <xsl:comment>Set servlet initialization and destruction listeners</xsl:comment>
                <xsl:call-template name="comment">
                    <xsl:with-param name="caption" select="'servlet listener processors'"/>
                    <xsl:with-param name="commented" select="$target != 'dev'"/>
                    <xsl:with-param name="content">
                        <init-param>
                            <param-name>oxf.servlet-initialized-processor.name</param-name>
                            <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                        </init-param>
                        <init-param>
                            <param-name>oxf.servlet-initialized-processor.input.config</param-name>
                            <param-value>oxf:/apps/context/servlet-initialized.xpl</param-value>
                        </init-param>
                        <init-param>
                            <param-name>oxf.servlet-destroyed-processor.name</param-name>
                            <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                        </init-param>
                        <init-param>
                            <param-name>oxf.servlet-destroyed-processor.input.config</param-name>
                            <param-value>oxf:/apps/context/servlet-destroyed.xpl</param-value>
                        </init-param>
                    </xsl:with-param>
                </xsl:call-template>
            </servlet>

            <xsl:comment>This is the XForms Renderer servlet, used to deploy Orbeon Forms as a separate WAR</xsl:comment>
            <servlet>
                <servlet-name>orbeon-renderer-servlet</servlet-name>
                <servlet-class>org.orbeon.oxf.servlet.OrbeonServlet</servlet-class>
                <xsl:comment>Set main processor</xsl:comment>
                <init-param>
                    <param-name>oxf.main-processor.name</param-name>
                    <param-value>{http://www.orbeon.com/oxf/processors}page-flow</param-value>
                </init-param>
                <init-param>
                    <param-name>oxf.main-processor.input.controller</param-name>
                    <param-value>oxf:/ops/xforms/xforms-renderer-page-flow.xml</param-value>
                </init-param>
                <xsl:comment>Set error processor</xsl:comment>
                <init-param>
                    <param-name>oxf.error-processor.name</param-name>
                    <param-value>{http://www.orbeon.com/oxf/processors}pipeline</param-value>
                </init-param>
                <init-param>
                    <param-name>oxf.error-processor.input.config</param-name>
                    <param-value>oxf:/config/error.xpl</param-value>
                </init-param>
            </servlet>

            <servlet>
                <servlet-name>display-chart-servlet</servlet-name>
                <servlet-class>org.jfree.chart.servlet.DisplayChart</servlet-class>
            </servlet>

            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'eXist XMLRPC support'"/>
                <xsl:with-param name="commented" select="$target != 'dev'"/>
                <xsl:with-param name="content">
                    <servlet>
                        <servlet-name>exist-xmlrpc-servlet</servlet-name>
                        <servlet-class>org.exist.xmlrpc.RpcServlet</servlet-class>
                    </servlet>
                </xsl:with-param>
            </xsl:call-template>

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
            </servlet>

            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'eXist WebDAV support'"/>
                <xsl:with-param name="commented" select="$target != 'dev'"/>
                <xsl:with-param name="content">
                    <servlet>
                        <servlet-name>exist-webdav-servlet</servlet-name>
                        <servlet-class>org.exist.http.servlets.WebDAVServlet</servlet-class>
                        <init-param>
                            <param-name>authentication</param-name>
                            <param-value>basic</param-value>
                        </init-param>
                    </servlet>
                </xsl:with-param>
            </xsl:call-template>

            <servlet-mapping>
                <servlet-name>orbeon-main-servlet</servlet-name>
                <url-pattern>/</url-pattern>
            </servlet-mapping>

            <servlet-mapping>
                <servlet-name>orbeon-renderer-servlet</servlet-name>
                <url-pattern>/xforms-renderer</url-pattern>
            </servlet-mapping>

            <servlet-mapping>
                <servlet-name>exist-rest-servlet</servlet-name>
                <url-pattern>/exist/rest/*</url-pattern>
            </servlet-mapping>

            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'eXist XMLRPC support'"/>
                <xsl:with-param name="commented" select="$target != 'dev'"/>
                <xsl:with-param name="content">
                    <servlet-mapping>
                        <servlet-name>exist-xmlrpc-servlet</servlet-name>
                        <url-pattern>/exist/xmlrpc</url-pattern>
                    </servlet-mapping>
                </xsl:with-param>
            </xsl:call-template>

            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'eXist WebDAV support'"/>
                <xsl:with-param name="commented" select="$target != 'dev'"/>
                <xsl:with-param name="content">
                    <servlet-mapping>
                        <servlet-name>exist-webdav-servlet</servlet-name>
                        <url-pattern>/exist/webdav/*</url-pattern>
                    </servlet-mapping>
                </xsl:with-param>
            </xsl:call-template>

            <servlet-mapping>
                <servlet-name>display-chart-servlet</servlet-name>
                <url-pattern>/chartDisplay</url-pattern>
            </servlet-mapping>

            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'relational persistence, and change oracle if necessary'"/>
                <xsl:with-param name="commented" select="$target != 'dev'"/>
                <xsl:with-param name="content">
                    <resource-ref>
                        <description>DataSource</description>
                        <res-ref-name>jdbc/oracle</res-ref-name>
                        <res-type>javax.sql.DataSource</res-type>
                        <res-auth>Container</res-auth>
                    </resource-ref>
                </xsl:with-param>
            </xsl:call-template>

            <xsl:call-template name="comment">
                <xsl:with-param name="caption" select="'Form Runner authentication'"/>
                <xsl:with-param name="commented" select="true()"/>
                <xsl:with-param name="content">
                    <security-constraint>
                        <web-resource-collection>
                            <web-resource-name>Form Runner pages</web-resource-name>
                            <url-pattern>/fr/*</url-pattern>
                        </web-resource-collection>
                        <auth-constraint>
                            <role-name>orbeon-user</role-name>
                        </auth-constraint>
                    </security-constraint>
                    <security-constraint>
                        <web-resource-collection>
                            <web-resource-name>Form Runner services and public pages and resources</web-resource-name>
                            <url-pattern>/fr/service/*</url-pattern>
                            <url-pattern>/fr/style/*</url-pattern>
                            <url-pattern>/fr/not-found</url-pattern>
                            <url-pattern>/fr/unauthorized</url-pattern>
                            <url-pattern>/fr/error</url-pattern>
                            <url-pattern>/fr/login</url-pattern>
                            <url-pattern>/fr/login-error</url-pattern>
                        </web-resource-collection>
                        <!-- No auth-constraint -->
                    </security-constraint>
                    <login-config>
                        <auth-method>FORM</auth-method>
                        <form-login-config>
                            <form-login-page>/fr/login</form-login-page>
                            <form-error-page>/fr/login-error</form-error-page>
                        </form-login-config>
                    </login-config>
                    <security-role>
                        <role-name>orbeon-user</role-name>
                    </security-role>
                </xsl:with-param>
            </xsl:call-template>

            <session-config>
                <!-- 1 * 60 = 1 hour -->
                <session-timeout>60</session-timeout>
            </session-config>

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
