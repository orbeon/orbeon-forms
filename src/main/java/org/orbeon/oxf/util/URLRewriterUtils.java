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

import org.apache.commons.lang3.StringUtils;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.controller.PageFlowControllerProcessor;
import org.orbeon.oxf.processor.RegexpMatcher;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.servlet.OrbeonXFormsFilter;
import org.orbeon.oxf.xforms.XFormsProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

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
        MATCH_ALL_PATH_MATCHER = new URLRewriterUtils.PathMatcher("/.*", null, true);
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

        // Case where a protocol is specified: the URL is left untouched
        if (NetUtils.urlHasProtocol(urlString))
            return urlString;

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
        return "separate".equals(attributes.get(OrbeonXFormsFilter.RendererDeploymentAttributeName()));
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
                final String version = isPlatformURL ? URLRewriterUtils.getOrbeonVersionForClient() : applicationVersion;
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
        return regexp != null && RegexpMatcher.jMatchResult(Pattern.compile(regexp), absolutePathNoContext).matches();
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
        return regexp != null && RegexpMatcher.jMatchResult(Pattern.compile(regexp), absolutePathNoContext).matches();
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

    public static String getRewritingContext(String rewritingStrategy, String defaultContext) {
        return Properties.instance().getPropertySet().getString(REWRITING_CONTEXT_PROPERTY_PREFIX + rewritingStrategy + REWRITING_CONTEXT_PROPERTY_SUFFIX, defaultContext);
    }

    public static boolean isWSRPEncodeResources() {
        return Properties.instance().getPropertySet().getBoolean(REWRITING_WSRP_ENCODE_RESOURCES_PROPERTY, WSRP_ENCODE_RESOURCES_DEFAULT);
    }

    public static String getServiceBaseURI() {
        return Properties.instance().getPropertySet().getStringOrURIAsString(REWRITING_SERVICE_BASE_URI_PROPERTY, REWRITING_SERVICE_BASE_URI_DEFAULT, false);
    }

    public static String getApplicationResourceVersion() {
        final String propertyString = Properties.instance().getPropertySet().getString(RESOURCES_VERSION_NUMBER_PROPERTY);
        return StringUtils.isBlank(propertyString) ? null : propertyString.trim();
    }

    // Return the version string either in clear or encoded with HMAC depending on configuration
    public static String getOrbeonVersionForClient() {
        final boolean isEncodeVersion = XFormsProperties.isEncodeVersion();
        return isEncodeVersion ? getHmacVersion() : Version.VersionNumber();
    }

    private static String getHmacVersion() {
        return SecureUtils.hmacString(Version.VersionNumber(), "hex");
    }

    public static List<URLRewriterUtils.PathMatcher> getMatchAllPathMatcher() {
        if (isResourcesVersioned()) {
            return MATCH_ALL_PATH_MATCHERS;
        } else {
            return null;
        }
    }

    public static class PathMatcher {
        public final String regexp;
        public final String mimeType;
        public final boolean versioned;

        public final Pattern pattern;

        /**
         * Construct from parameters.
         *
         * @param regexp    regexp pattern to match
         * @param mimeType  mediatype
         * @param versioned the resource is versioned
         */
        public PathMatcher(String regexp, String mimeType, boolean versioned) {
            this.regexp = regexp;
            this.mimeType = mimeType;
            this.versioned = versioned;

            this.pattern = Pattern.compile(regexp);
        }
    }
    
    public static boolean isVersionedURL(String absolutePathNoContext, List<URLRewriterUtils.PathMatcher> pathMatchers) {
        for (final URLRewriterUtils.PathMatcher pathMatcher : pathMatchers) {
            if (RegexpMatcher.jMatchResult(pathMatcher.pattern, absolutePathNoContext).matches())
                return true;
        }
        
        return false;
    }
    
    public static List<URLRewriterUtils.PathMatcher> getPathMatchers() {
        final List<URLRewriterUtils.PathMatcher> pathMatchers = (List<URLRewriterUtils.PathMatcher>) PipelineContext.get().getAttribute(PageFlowControllerProcessor.PathMatchers());
        return (pathMatchers != null) ? pathMatchers : URLRewriterUtils.EMPTY_PATH_MATCHER_LIST;
    }

    // Get path matchers from the pipeline context
    public static Callable<List<PathMatcher>> getPathMatchersCallable()  {
        return new Callable<List<URLRewriterUtils.PathMatcher>>() {
            public List<URLRewriterUtils.PathMatcher> call() throws Exception {
                return URLRewriterUtils.getPathMatchers();
            }
        };
    }

    /*
     * Copyright 2000-2005 The Apache Software Foundation
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *     http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
    public static String globToRegexp(char[] pattern) {
        int ch;
        StringBuffer buffer;

        buffer = new StringBuffer(2 * pattern.length);
        boolean inCharSet = false;

        boolean questionMatchesZero = false;
        boolean starCannotMatchNull = false;

        for (ch = 0; ch < pattern.length; ch++) {
            switch (pattern[ch]) {
                case '*':
                    if (inCharSet)
                        buffer.append('*');
                    else {
                        if (starCannotMatchNull)
                            buffer.append(".+");
                        else
                            buffer.append(".*");
                    }
                    break;
                case '?':
                    if (inCharSet)
                        buffer.append('?');
                    else {
                        if (questionMatchesZero)
                            buffer.append(".?");
                        else
                            buffer.append('.');
                    }
                    break;
                case '[':
                    inCharSet = true;
                    buffer.append(pattern[ch]);

                    if (ch + 1 < pattern.length) {
                        switch (pattern[ch + 1]) {
                            case '!':
                            case '^':
                                buffer.append('^');
                                ++ch;
                                continue;
                            case ']':
                                buffer.append(']');
                                ++ch;
                                continue;
                        }
                    }
                    break;
                case ']':
                    inCharSet = false;
                    buffer.append(pattern[ch]);
                    break;
                case '\\':
                    buffer.append('\\');
                    if (ch == pattern.length - 1) {
                        buffer.append('\\');
                    } else if (__isGlobMetaCharacter(pattern[ch + 1]))
                        buffer.append(pattern[++ch]);
                    else
                        buffer.append('\\');
                    break;
                default:
                    if (!inCharSet && __isPerl5MetaCharacter(pattern[ch]))
                        buffer.append('\\');
                    buffer.append(pattern[ch]);
                    break;
            }
        }

        return buffer.toString();
    }

    private static boolean __isPerl5MetaCharacter(char ch) {
        return ("'*?+[]()|^$.{}\\".indexOf(ch) >= 0);
    }

    private static boolean __isGlobMetaCharacter(char ch) {
        return ("*?[]".indexOf(ch) >= 0);
    }
}