/**
 *  Copyright (C) 2008 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util;

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.properties.Properties;
import org.dom4j.QName;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Iterator;

/**
 * Utility class to rewrite URLs.
 */
public class URLRewriterUtils {

    // Versioned resources configuration
    public static final String RESOURCES_VERSIONED_PROPERTY = "oxf.resources.versioned";
    public final static boolean RESOURCES_VERSIONED_DEFAULT = false;

    private static final String REWRITING_STRATEGY_PROPERTY_PREFIX = "oxf.url-rewriting.strategy.";
    private static final String REWRITING_CONTEXT_PROPERTY_PREFIX = "oxf.url-rewriting.";
    private static final String REWRITING_CONTEXT_PROPERTY_SUFFIX = ".context";

    public static final String RESOURCES_VERSION_NUMBER_PROPERTY = "oxf.resources.version-number";

    /**
     * Rewrite a URL based on the request URL, a URL string, and a rewriting mode.
     *
     * @param request       incoming request
     * @param urlString     URL string to rewrite
     * @param rewriteMode   rewrite mode (see ExternalContext.Response)
     * @return              rewritten URL string
     */
    public static String rewriteURL(ExternalContext.Request request, String urlString, int rewriteMode) {
        // Case where a protocol is specified: the URL is left untouched in any case
        if (NetUtils.urlHasProtocol(urlString))
            return urlString;

        try {
            final String baseURLString;
            {
                String _baseURLString;
                // Prepend absolute base if needed
                if (rewriteMode == ExternalContext.Response.REWRITE_MODE_ABSOLUTE) {
                    _baseURLString = request.getScheme() + "://" + request.getServerName() + (request.getServerPort() == 80 ? "" : ":" + request.getServerPort());
                } else {
                    _baseURLString = "";
                }
                // Append context path if needed
                if (rewriteMode != ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT)
                    _baseURLString = _baseURLString + request.getContextPath();

                baseURLString = _baseURLString;
            }

            // Return absolute path URI with query string and fragment identifier if needed
            if (urlString.startsWith("?")) {
                // This is a special case that appears to be implemented
                // in Web browsers as a convenience. Users may use it.
                return baseURLString + request.getRequestPath() + urlString;
            } else if (rewriteMode == ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE && !urlString.startsWith("/") && !"".equals(urlString)) {
                // Don't change the URL if it is a relative path and we don't force absolute
                return urlString;
            } else {
                // Regular case, parse the URL

                final URI baseURIWithPath = new URI("http", "example.org", request.getRequestPath(), null);
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
     * @param response          outgoing response (for rewriting resource URLs)
     * @param urlString         URL string to rewrite
     * @param pathMatchers      List of PathMatcher
     * @return                  rewritten URL string
     */
    public static String rewriteResourceURL(ExternalContext.Request request, ExternalContext.Response response, String urlString, List pathMatchers) {
        if (pathMatchers != null && pathMatchers.size() > 0) {
            // We need to match the URL string against the matcher

            // 1. Rewrite to absolute path URI without context
            final String absoluteURINoContext = response.rewriteResourceURL(urlString, ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT);
            if (NetUtils.urlHasProtocol(absoluteURINoContext))
                return absoluteURINoContext;

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
                // These special URLs must not be rewritten
                return response.rewriteResourceURL(urlString, ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
            }

            // 2. Determine if URL is a platform or application URL based on reserved paths
            final boolean isPlatformURL = absolutePathNoContext.startsWith("/ops/") || absolutePathNoContext.startsWith("/config/");

            // TODO: get version only once for a run
            final String applicationVersion = getApplicationResourceVersion();
            if (!isPlatformURL && (applicationVersion == null || applicationVersion.length() == 0)) {
                // There is no application version so do usual rewrite
                return response.rewriteResourceURL(urlString, ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
            }

            // 3. Iterate through matchers and see if we get a match
            for (Iterator i = pathMatchers.iterator(); i.hasNext();) {
                final PathMatcher pathMatcher = (PathMatcher) i.next();

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

                    // Instanciate matcher processor to parallel what's done in the Page Flow
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

                    final String contextPath = request.getContextPath();
                    final String version = isPlatformURL ? Version.getVersion() : applicationVersion;

                    return contextPath + "/" + version + absoluteURINoContext;
                }
            }

            // No match found, perform regular rewrite
            return response.rewriteResourceURL(urlString, ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        } else {
            // No Page Flow context, perform regular rewrite
            return response.rewriteResourceURL(urlString, ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        }
    }

    public static boolean isResourcesVersioned() {
        return Properties.instance().getPropertySet().getBoolean(RESOURCES_VERSIONED_PROPERTY, RESOURCES_VERSIONED_DEFAULT).booleanValue();
    }

    public static String getRewritingStrategy(String containerType, String defaultStrategy) {
        return Properties.instance().getPropertySet().getString(REWRITING_STRATEGY_PROPERTY_PREFIX + containerType, defaultStrategy);
    }

    public static String getRewritingContext(String rewritingStrategy, String defaultContext) {
        return Properties.instance().getPropertySet().getString(REWRITING_CONTEXT_PROPERTY_PREFIX + rewritingStrategy + REWRITING_CONTEXT_PROPERTY_SUFFIX, defaultContext);
    }

    public static String getApplicationResourceVersion() {
        return Properties.instance().getPropertySet().getString(RESOURCES_VERSION_NUMBER_PROPERTY);
    }

    public static class PathMatcher {
        public String pathInfo;
        public QName matcher;
        public String mimeType;
        public boolean versioned;

        public PathMatcher(String pathInfo, QName matcher, String mimeType, boolean versioned) {
            this.pathInfo = pathInfo;
            this.matcher = matcher;
            this.mimeType = mimeType;
            this.versioned = versioned;
        }
    }
}