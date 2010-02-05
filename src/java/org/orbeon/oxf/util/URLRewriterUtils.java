/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util;

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.processor.MatchProcessor;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorFactory;
import org.orbeon.oxf.processor.ProcessorFactoryRegistry;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.servlet.OrbeonXFormsFilter;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Utility class to rewrite URLs.
 */
public class URLRewriterUtils {

    // Versioned resources configuration
    public static final String RESOURCES_VERSIONED_PROPERTY = "oxf.resources.versioned";
    public static final boolean RESOURCES_VERSIONED_DEFAULT = false;

    private static final String REWRITING_STRATEGY_PROPERTY_PREFIX = "oxf.url-rewriting.strategy.";
    private static final String REWRITING_CONTEXT_PROPERTY_PREFIX = "oxf.url-rewriting.";
    private static final String REWRITING_CONTEXT_PROPERTY_SUFFIX = ".context";

    public static final String RESOURCES_VERSION_NUMBER_PROPERTY = "oxf.resources.version-number";

    public static final String REWRITING_SERVICE_BASE_URI_PROPERTY = "oxf.url-rewriting.service.base-uri";
    public static final String REWRITING_SERVICE_BASE_URI_DEFAULT = "";

    public static final List<PathMatcher> EMPTY_PATH_MATCHER_LIST = Collections.emptyList();

    private static final PathMatcher MATCH_ALL_PATH_MATCHER;
    private static final List<URLRewriterUtils.PathMatcher> MATCH_ALL_PATH_MATCHERS;

    static {
        MATCH_ALL_PATH_MATCHER = new URLRewriterUtils.PathMatcher("/*", null, null, true);
        MATCH_ALL_PATH_MATCHERS = Collections.singletonList(URLRewriterUtils.MATCH_ALL_PATH_MATCHER);
    }

    /**
     * Rewrite a URL based on the request URL, a URL, and a rewriting mode.
     *
     * @param request       incoming request
     * @param urlString     URL to rewrite
     * @param rewriteMode   rewrite mode (see ExternalContext.Response)
     * @return              rewritten URL
     */
    public static String rewriteURL(ExternalContext.Request request, String urlString, int rewriteMode) {
        return rewriteURL(request.getScheme(), request.getServerName(), request.getServerPort(), request.getClientContextPath(urlString),
                request.getRequestPath(), urlString, rewriteMode);
    }

    /**
     * Rewrite a service URL. The URL is rewritten against a base URL which is:
     *
     * o specified externally or
     * o the incoming request if not specified externally
     *
     * @param request           incoming request
     * @param urlString         URL to rewrite
     * @param forceAbsolute     whether to force an absolute URL in case the request is used as a base
     * @return                  rewritten URL
     */
    public static String rewriteServiceURL(ExternalContext.Request request, String urlString, boolean forceAbsolute) {

        final int rewriteMode = forceAbsolute ? ExternalContext.Response.REWRITE_MODE_ABSOLUTE : ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH;

        final String baseURIProperty = getServiceBaseURI();
        if (org.apache.commons.lang.StringUtils.isBlank(baseURIProperty)) {
            // Property not specified, use request to build base URI
            return rewriteURL(request.getScheme(), request.getServerName(), request.getServerPort(), request.getClientContextPath(urlString),
                    request.getRequestPath(), urlString, rewriteMode);
        } else {
            // Property specified
            try {
                final URI baseURI = new URI(baseURIProperty.trim());
                // NOTE: Force absolute URL to be returned in this case anyway
                return rewriteURL(baseURI.getScheme() != null ? baseURI.getScheme() : request.getScheme(),
                        baseURI.getHost() != null ? baseURI.getHost() : request.getServerName(),
                        baseURI.getHost() != null ? baseURI.getPort() : request.getServerPort(),
                        baseURI.getPath(), "", urlString, ExternalContext.Response.REWRITE_MODE_ABSOLUTE);

            } catch (URISyntaxException e) {
                throw new OXFException("Incorrect base URI property specified: " + baseURIProperty);
            }
        }
    }

    /**
     * Return a context path as seen by the client. This might be the current request's context path, or the forwarding
     * servlet's context path. The returned path might be different for Orbeon resources vs. application resources.
     *
     * @param request           current request.
     * @param isPlatformPath    whether the URL is a platform path
     * @return                  context path
     */
    public static String getClientContextPath(ExternalContext.Request request, boolean isPlatformPath) {
        final Map<String, Object> attributes = request.getAttributesMap();
        // NOTE: We don't check on javax.servlet.include.context_path, because that attribute behaves very differently:
        // in the case of includes, it represents properties of the included servlet, not the values of the including
        // servlet.
        final String sourceContextPath = (String) attributes.get("javax.servlet.forward.context_path");
        if (sourceContextPath != null) {
            // We were forwarded to
            final boolean isSeparateDeployment = "separate".equals(attributes.get(OrbeonXFormsFilter.RENDERER_DEPLOYMENT_ATTRIBUTE_NAME));
            if (isPlatformPath && isSeparateDeployment) {
                // This is the case of forwarding in separate deployment: Orbeon resources are forwarded
                // E.g. /foobar/orbeon
                return sourceContextPath + request.getContextPath();
            } else if (isPlatformPath) {
                // This is the case of forwarding without separate deployment: Orbeon resources are loaded by the Orbeon context
                // E.g. /orbeon
                return request.getContextPath();
            } else {
                // This is the case of application resources: they are loaded from the original context
                // E.g. /foobar
                return sourceContextPath;
            }
        } else {
            // We were not forwarded to
            return request.getContextPath();
        }
    }

    public static boolean isForwarded(ExternalContext.Request request) {
        // NOTE: We don't check on javax.servlet.include.context_path, because that attribute behaves very differently:
        // in the case of includes, it represents properties of the included servlet, not the values of the including
        // servlet.
        final String sourceContextPath = (String) request.getAttributesMap().get("javax.servlet.forward.context_path");
        return sourceContextPath != null;
    }

    /**
     * Rewrite a URL based on a base URL, a URL, and a rewriting mode.
     *
     * @param scheme            base URL scheme
     * @param host              base URL host
     * @param port              base URL port
     * @param contextPath       base URL context path
     * @param requestPath       base URL request path
     * @param urlString         URL to rewrite
     * @param rewriteMode       rewrite mode (see ExternalContext.Response)
     * @return                  rewritten URL
     */
    private static String rewriteURL(String scheme, String host, int port, String contextPath, String requestPath, String urlString, int rewriteMode) {
        // Case where a protocol is specified: the URL is left untouched in any case
        if (NetUtils.urlHasProtocol(urlString))
            return urlString;

        try {
            final String baseURLString;
            {
                String _baseURLString;
                // Prepend absolute base if needed
                if (rewriteMode == ExternalContext.Response.REWRITE_MODE_ABSOLUTE) {
                    _baseURLString = scheme + "://" + host + ((port == 80 || port == -1) ? "" : ":" + port);
                } else {
                    _baseURLString = "";
                }
                // Append context path if needed
                if (rewriteMode != ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT)
                    _baseURLString = _baseURLString + contextPath;

                baseURLString = _baseURLString;
            }

            // Return absolute path URI with query string and fragment identifier if needed
            if (urlString.startsWith("?")) {
                // This is a special case that appears to be implemented
                // in Web browsers as a convenience. Users may use it.
                return baseURLString + requestPath + urlString;
            } else if (rewriteMode == ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE && !urlString.startsWith("/") && !"".equals(urlString)) {
                // Don't change the URL if it is a relative path and we don't force absolute
                return urlString;
            } else {
                // Regular case, parse the URL

                final URI baseURIWithPath = new URI("http", "example.org", requestPath, null);
                final URI resolvedURI = baseURIWithPath.resolve(urlString).normalize();// normalize to remove "..", etc.

                // Append path, query and fragment
                final String query = resolvedURI.getRawQuery();
                final String fragment = resolvedURI.getRawFragment();
                final String tempResult = resolvedURI.getRawPath()
                        + ((query != null) ? "?" + query : "")
                        + ((fragment != null) ? "#" + fragment : "");

                // Prepend base
                return baseURLString + tempResult;
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Rewrite a resource URL, possibly with version information, based on the incoming request as well as a list of
     * path matchers.
     *
     * NOTE: As of April 2009, this is only used in Servlet mode.
     *
     * @param request           incoming request
     * @param urlString         URL to rewrite
     * @param pathMatchers      List of PathMatcher
     * @param rewriteMode       rewrite mode
     * @return                  rewritten URL
     */
    public static String rewriteResourceURL(ExternalContext.Request request, String urlString, List<URLRewriterUtils.PathMatcher> pathMatchers, int rewriteMode) {
        if (pathMatchers != null && pathMatchers.size() > 0) {
            // We need to match the URL against the matcher

            // 1. Rewrite to absolute path URI without context
            final String absoluteURINoContext = rewriteURL(request, urlString, ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT);
            if (NetUtils.urlHasProtocol(absoluteURINoContext))
                return absoluteURINoContext; // will be an absolute path

            // Obtain just the path
            final String absolutePathNoContext;
            {
                final URI absoluteURINoContextURI;
                try {
                    absoluteURINoContextURI = new URI(absoluteURINoContext);
                } catch (URISyntaxException e) {
                    throw new OXFException(e);
                }
                absolutePathNoContext = absoluteURINoContextURI.getPath();
            }

            if (absolutePathNoContext.startsWith("/xforms-server/")) {
                // Special URL must not be rewritten as resource
                // TODO: when is this hit?
                return rewriteURL(request, urlString, rewriteMode);
            }

            // 2. Determine if URL is a platform or application URL based on reserved paths
            final boolean isPlatformURL = isPlatformPath(absolutePathNoContext);

            // TODO: get version only once for a run
            final String applicationVersion = getApplicationResourceVersion();
            if (!isPlatformURL && applicationVersion == null) {
                // There is no application version so do usual rewrite
                return rewriteURL(request, urlString, rewriteMode);
            }

            // 3. Iterate through matchers and see if we get a match
            for (PathMatcher pathMatcher: pathMatchers) {

                final boolean isMatch;
                if (pathMatcher.matcher == null) {
                    // There is no matcher, use basic rules

                    if (pathMatcher.pathInfo.startsWith("*")) {
                        // Extension match
                        isMatch = absolutePathNoContext.endsWith(pathMatcher.pathInfo.substring(1));
                    } else if (pathMatcher.pathInfo.endsWith("*")) {
                        // Partial match
                        isMatch = absolutePathNoContext.startsWith(pathMatcher.pathInfo.substring(0, pathMatcher.pathInfo.length() - 1));
                    } else {
                        // Exact match
                        isMatch = absolutePathNoContext.equals(pathMatcher.pathInfo);
                    }
                } else {
                    // Use matcher

                    // Instantiate matcher processor to parallel what's done in the Page Flow
                    final ProcessorFactory processorFactory = ProcessorFactoryRegistry.lookup(pathMatcher.matcher);
                    if (processorFactory == null)
                        throw new OXFException("Cannot find processor factory with name '"
                                + pathMatcher.matcher.getNamespacePrefix() + ":" + pathMatcher.matcher.getName() + "'");

                    final Processor processor = processorFactory.createInstance();
                    if (processor instanceof MatchProcessor) {
                        final MatchProcessor matcherProcessor = (MatchProcessor) processor;

                        // Perform the test
                        isMatch = matcherProcessor.match(pathMatcher.pathInfo, absolutePathNoContext).matches;
                    } else {
                        throw new OXFException("Matcher processor is not an instance of MatchProcessor'"
                                + pathMatcher.matcher.getNamespacePrefix() + ":" + pathMatcher.matcher.getName() + "'");
                    }
                }

                if (isMatch) {
                    // 4. Found a match, perform additional rewrite at the beginning

                    final String version = isPlatformURL ? Version.getVersion() : applicationVersion;
                    // Call full method so that we can get the proper client context path
                    return rewriteURL(request.getScheme(), request.getServerName(), request.getServerPort(),
                            request.getClientContextPath(urlString), request.getRequestPath(), "/" + version + absoluteURINoContext, rewriteMode);
                }
            }

            // No match found, perform regular rewrite
            return rewriteURL(request, urlString, rewriteMode);
        } else {
            // No Page Flow context, perform regular rewrite
            return rewriteURL(request, urlString, rewriteMode);
        }
    }

    // TODO: add test for /forms/orbeon; should make this a property!
    public static final String[] PLATFORM_PATHS = {
        "/ops/", "/config/", "/xbl/orbeon/", "/xforms-server"
    };

    /**
     * Check if the given path is a platform path (as opposed to a user application path).
     *
     * @param absolutePathNoContext path to check
     * @return                      true iif path is a platform path
     */
    public static boolean isPlatformPath(String absolutePathNoContext) {
        for (final String path: PLATFORM_PATHS) {
            if (absolutePathNoContext.startsWith(path))
                return true;
        }
        return false;
    }

//    public static String getPlatformPathsAsSequence() {
//        final StringBuilder sb = new StringBuilder("(");
//        for (final String path: PLATFORM_PATHS) {
//            sb.append('\'');
//            sb.append(path);
//            sb.append('\'');
//        }
//        sb.append(')');
//        return sb.toString();
//        // XXX issue: PFC tests tokenize($path, '/')[3] = ('ops', 'config'), need better way
//    }

    public static boolean isResourcesVersioned() {
        return Properties.instance().getPropertySet().getBoolean(RESOURCES_VERSIONED_PROPERTY, RESOURCES_VERSIONED_DEFAULT);
    }

    public static String getRewritingStrategy(String containerType, String defaultStrategy) {
        return Properties.instance().getPropertySet().getString(REWRITING_STRATEGY_PROPERTY_PREFIX + containerType, defaultStrategy);
    }

    public static String getRewritingContext(String rewritingStrategy, String defaultContext) {
        return Properties.instance().getPropertySet().getString(REWRITING_CONTEXT_PROPERTY_PREFIX + rewritingStrategy + REWRITING_CONTEXT_PROPERTY_SUFFIX, defaultContext);
    }

    public static String getServiceBaseURI() {
        return Properties.instance().getPropertySet().getStringOrURIAsString(REWRITING_SERVICE_BASE_URI_PROPERTY, REWRITING_SERVICE_BASE_URI_DEFAULT);
    }

    public static String getApplicationResourceVersion() {
        final String propertyString = Properties.instance().getPropertySet().getString(RESOURCES_VERSION_NUMBER_PROPERTY);
        return org.apache.commons.lang.StringUtils.isBlank(propertyString) ? null : propertyString.trim();
    }

    public static List<URLRewriterUtils.PathMatcher> getMatchAllPathMatcher() {
        if (isResourcesVersioned()) {
            return MATCH_ALL_PATH_MATCHERS;
        } else {
            return null;
        }
    }

    public static class PathMatcher {
        public String pathInfo;
        public QName matcher;
        public String mimeType;
        public boolean versioned;

        /**
         * Construct from parameters.
         *
         * @param pathInfo      path to match on
         * @param matcher       type of matcher, or null for basic matching rules
         * @param mimeType      mediatype
         * @param versioned     whether the resource is versioned
         */
        public PathMatcher(String pathInfo, QName matcher, String mimeType, boolean versioned) {
            this.pathInfo = pathInfo;
            this.matcher = matcher;
            this.mimeType = mimeType;
            this.versioned = versioned;
        }

        /**
         * Construct from a serialized element.
         *
         * @param element   element containing serialized parameters
         */
        public PathMatcher(Element element) {
            this.pathInfo = element.attributeValue("path");
            final String matcherAttribute = element.attributeValue("matcher");
            this.matcher = (matcherAttribute != null) ? Dom4jUtils.explodedQNameToQName(matcherAttribute) : null;
            this.mimeType = element.attributeValue("type");
            this.versioned = "true".equals(element.attributeValue("versioned"));
        }

        /**
         * Serialize to an element.
         *
         * @return  element containing serialized parameters
         */
        public Element serialize() {
            final Element matcherElement = Dom4jUtils.createElement("matcher");

            matcherElement.addAttribute("path", pathInfo);
            if (matcher != null)
                matcherElement.addAttribute("matcher", Dom4jUtils.qNameToExplodedQName(matcher));
            matcherElement.addAttribute("type", mimeType);
            if (versioned)
                matcherElement.addAttribute("versioned", Boolean.toString(versioned));

            return matcherElement;
        }
    }
}