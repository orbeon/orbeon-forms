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

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
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
    public static final boolean WSRP_ENCODE_RESOURCES_DEFAULT = false;

    private static final String REWRITING_PLATFORM_PATHS_PROPERTY = "oxf.url-rewriting.platform-paths";
    private static final String REWRITING_APP_PATHS_PROPERTY = "oxf.url-rewriting.app-paths";
    private static final String REWRITING_APP_PREFIX_PROPERTY = "oxf.url-rewriting.app-prefix";
    private static final String REWRITING_STRATEGY_PROPERTY_PREFIX = "oxf.url-rewriting.strategy.";
    private static final String REWRITING_CONTEXT_PROPERTY_PREFIX = "oxf.url-rewriting.";
    private static final String REWRITING_CONTEXT_PROPERTY_SUFFIX = ".context";
    private static final String REWRITING_WSRP_ENCODE_RESOURCES_PROPERTY = "oxf.url-rewriting.wsrp.encode-resources";

    public static final String RESOURCES_VERSION_NUMBER_PROPERTY = "oxf.resources.version-number";

    public static final String REWRITING_SERVICE_BASE_URI_PROPERTY = "oxf.url-rewriting.service.base-uri";
    public static final String REWRITING_SERVICE_BASE_URI_DEFAULT = "";

    public static final List<PathMatcher> EMPTY_PATH_MATCHER_LIST = Collections.emptyList();

    private static final PathMatcher MATCH_ALL_PATH_MATCHER;
    public static final List<URLRewriterUtils.PathMatcher> MATCH_ALL_PATH_MATCHERS;

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
     * @param rewriteMode       rewrite mode
     * @return                  rewritten URL
     */
    public static String rewriteServiceURL(ExternalContext.Request request, String urlString, int rewriteMode) {

        assert (rewriteMode & ExternalContext.Response.REWRITE_MODE_ABSOLUTE) != 0;

        final String baseURIProperty = getServiceBaseURI();
        if (StringUtils.isBlank(baseURIProperty)) {
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
                        baseURI.getPath(), "", urlString, rewriteMode);

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
        if (sourceContextPath != null && isSeparateDeployment(request)) {
            // This is the case of forwarding in separate deployment
            if (isPlatformPath) {
                // Orbeon resources are forwarded
                // E.g. /foobar/orbeon
                return sourceContextPath + request.getContextPath();
            } else {
                // Application resources are loaded from the original context
                // E.g. /foobar
                return sourceContextPath;
            }
        } else {
            // We were not forwarded to or we were forwarded but we are not in separate deployment
            return request.getContextPath();
        }
    }

    public static boolean isSeparateDeployment(ExternalContext.Request request) {
        final Map<String, Object> attributes = request.getAttributesMap();
        return "separate".equals(attributes.get(OrbeonXFormsFilter.RENDERER_DEPLOYMENT_ATTRIBUTE_NAME));
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
     * @param urlString         URL to rewrite (accept human-readable URI)
     * @param rewriteMode       rewrite mode (see ExternalContext.Response)
     * @return                  rewritten URL
     */
    private static String rewriteURL(String scheme, String host, int port, String contextPath, String requestPath, String urlString, int rewriteMode) {
        // Accept human-readable URI
        urlString = NetUtils.encodeHRRI(urlString, true);

        // Case where a protocol is specified: the URL is left untouched (except for human-readable processing)
        if (NetUtils.urlHasProtocol(urlString))
            return urlString;

        try {
            final String baseURLString;
            {
                String _baseURLString;
                // Prepend absolute base if needed
                if ((rewriteMode & ExternalContext.Response.REWRITE_MODE_ABSOLUTE) != 0) {
                    _baseURLString = scheme + "://" + host + ((port == 80 || port == -1) ? "" : ":" + port);
                } else {
                    _baseURLString = "";
                }
                // Append context path if needed
                if ((rewriteMode & ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT) == 0)
                    _baseURLString = _baseURLString + contextPath;

                baseURLString = _baseURLString;
            }

            // Return absolute path URI with query string and fragment identifier if needed
            if (urlString.startsWith("?")) {
                // This is a special case that appears to be implemented
                // in Web browsers as a convenience. Users may use it.
                return baseURLString + requestPath + urlString;
            } else if ((rewriteMode & ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE) != 0 && !urlString.startsWith("/") && !"".equals(urlString)) {
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
            if (!isPlatformURL && (applicationVersion == null || isSeparateDeployment(request))) {
                // There is no application version OR we are in separate deployment so do usual rewrite
                return rewriteURL(request, urlString, rewriteMode);
            }

            // 3. Iterate through matchers and see if we get a match
            if (isVersionedURL(absolutePathNoContext, pathMatchers)) {
                // 4. Found a match, perform additional rewrite at the beginning
                final String version = isPlatformURL ? Version.getVersionNumber() : applicationVersion;
                // Call full method so that we can get the proper client context path
                return rewriteURL(request.getScheme(), request.getServerName(), request.getServerPort(),
                        request.getClientContextPath(urlString), request.getRequestPath(), "/" + version + absoluteURINoContext, rewriteMode);
            }

            // No match found, perform regular rewrite
            return rewriteURL(request, urlString, rewriteMode);
        } else {
            // No Page Flow context, perform regular rewrite
            return rewriteURL(request, urlString, rewriteMode);
        }
    }

    /**
     * Check if the given path is a platform path (as opposed to a user application path).
     *
     * @param absolutePathNoContext path to check
     * @return                      true iif path is a platform path
     */
    public static boolean isPlatformPath(String absolutePathNoContext) {
        final String regexp = Properties.instance().getPropertySet().getString(REWRITING_PLATFORM_PATHS_PROPERTY, null);
        // TODO: do not compile the regexp every time
        return regexp != null && new Perl5MatchProcessor().match(regexp, absolutePathNoContext).matches;
    }

    /**
     * Check if the given path is an application path, assuming it is not already a platform path.
     *
     * @param absolutePathNoContext path to check
     * @return                      true iif path is a platform path
     */
    public static boolean isNonPlatformPathAppPath(String absolutePathNoContext) {
        final String regexp = Properties.instance().getPropertySet().getString(REWRITING_APP_PATHS_PROPERTY, null);
        // TODO: do not compile the regexp every time
        return regexp != null && new Perl5MatchProcessor().match(regexp, absolutePathNoContext).matches;
    }

    /**
     * Decode an absolute path with no context, depending on whether there is an app version or not.
     *
     * @param absolutePathNoContext path
     * @param isVersioned           whether the resource is versioned or not
     * @return                      decoded path, or initial path if no decoding needed
     */
    public static String decodeResourceURI(String absolutePathNoContext, boolean isVersioned) {
        if (isVersioned) {
            // Versioned case
            final boolean hasApplicationVersion = URLRewriterUtils.getApplicationResourceVersion() != null;
            if (hasApplicationVersion) {
                // Remove version on any path
                return prependAppPathIfNeeded(removeVersionPrefix(absolutePathNoContext));
            } else {
                // Try to remove version then test for platform path
                final String pathWithVersionRemoved = removeVersionPrefix(absolutePathNoContext);
                if (isPlatformPath(pathWithVersionRemoved)) {
                    // This was a versioned platform path
                    return pathWithVersionRemoved;
                } else {
                    // Not a versioned platform path
                    // Don't remove version
                    return prependAppPathIfNeeded(absolutePathNoContext);
                }
            }
        } else {
            // Non-versioned case
            return prependAppPathIfNeeded(absolutePathNoContext);
        }
    }

    private static String prependAppPathIfNeeded(String path) {
        if (isPlatformPath(path) || isNonPlatformPathAppPath(path)) {
            // Path doesn't need adjustment
            return path;
        } else {
            // Adjust to make an app path
            return Properties.instance().getPropertySet().getString(REWRITING_APP_PREFIX_PROPERTY, "") + path;
        }
    }

    private static String removeVersionPrefix(String absolutePathNoContext) {
        if (absolutePathNoContext.length() == 0) {
            return absolutePathNoContext;
        } else {
            final int slashIndex = absolutePathNoContext.indexOf('/', 1);
            return (slashIndex != -1) ? absolutePathNoContext.substring(slashIndex) : absolutePathNoContext;
        }
    }

    public static boolean isResourcesVersioned() {
        final boolean requested = Properties.instance().getPropertySet().getBoolean(RESOURCES_VERSIONED_PROPERTY, RESOURCES_VERSIONED_DEFAULT);
        return Version.instance().isPEFeatureEnabled(requested, RESOURCES_VERSIONED_PROPERTY);
    }

    public static String getRewritingStrategy(String containerType, String defaultStrategy) {
        return Properties.instance().getPropertySet().getString(REWRITING_STRATEGY_PROPERTY_PREFIX + containerType, defaultStrategy);
    }

    public static String getRewritingContext(String rewritingStrategy, String defaultContext) {
        return Properties.instance().getPropertySet().getString(REWRITING_CONTEXT_PROPERTY_PREFIX + rewritingStrategy + REWRITING_CONTEXT_PROPERTY_SUFFIX, defaultContext);
    }

    public static boolean isWSRPEncodeResources() {
        return Properties.instance().getPropertySet().getBoolean(REWRITING_WSRP_ENCODE_RESOURCES_PROPERTY, WSRP_ENCODE_RESOURCES_DEFAULT);
    }

    public static String getServiceBaseURI() {
        return Properties.instance().getPropertySet().getStringOrURIAsString(REWRITING_SERVICE_BASE_URI_PROPERTY, REWRITING_SERVICE_BASE_URI_DEFAULT);
    }

    public static String getApplicationResourceVersion() {
        final String propertyString = Properties.instance().getPropertySet().getString(RESOURCES_VERSION_NUMBER_PROPERTY);
        return StringUtils.isBlank(propertyString) ? null : propertyString.trim();
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
    
    public static boolean isVersionedURL(String absolutePathNoContext, List<URLRewriterUtils.PathMatcher> pathMatchers) {
        for (final URLRewriterUtils.PathMatcher pathMatcher : pathMatchers) {

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
            
            if (isMatch)
                return true;
        }
        
        return false;
    }
    
    public static List<URLRewriterUtils.PathMatcher> getPathMatchers() {
        final List<URLRewriterUtils.PathMatcher> pathMatchers = (List<URLRewriterUtils.PathMatcher>) PipelineContext.get().getAttribute(PageFlowControllerProcessor.PATH_MATCHERS);
        return (pathMatchers != null) ? pathMatchers : URLRewriterUtils.EMPTY_PATH_MATCHER_LIST;
    }
}