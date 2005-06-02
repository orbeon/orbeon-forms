/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.processor;

import com.sun.image.codec.jpeg.JPEGCodec;
import com.sun.image.codec.jpeg.JPEGEncodeParam;
import com.sun.image.codec.jpeg.JPEGImageDecoder;
import com.sun.image.codec.jpeg.JPEGImageEncoder;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.orbeon.oxf.cache.SoftCacheImpl;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.NumberUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataDocument;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.*;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;
import java.util.List;

/**
 * ImageServer serves images from URLs while performing various operations on
 * them such as scaling or cropping. It also handles a disk cache of
 * transformed images.
 *
 * NOTE: The JPEG quality parameter only applies when a transformation is
 * done. There is no provision to do a quality conversion only.
 */
public class ImageServer extends ProcessorImpl {

    private static Logger logger = LoggerFactory.createLogger(ImageServer.class);

    public static final String IMAGE_SERVER_CONFIG_NAMESPACE_URI = "http://orbeon.org/oxf/xml/image-server-config";
    public static final String IMAGE_SERVER_IMAGE_NAMESPACE_URI = "http://orbeon.org/oxf/xml/image-server-image";

    private static final String INPUT_IMAGE = "image";

    private static final float DEFAULT_QUALITY = 0.5f;
    private static final boolean DEFAULT_USE_SANDBOX = true;
    private static final boolean DEFAULT_USE_CACHE = true;
    private static final boolean DEFAULT_SCALE_UP = true;

    private SoftCacheImpl cache;

    public ImageServer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, IMAGE_SERVER_CONFIG_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_IMAGE, IMAGE_SERVER_IMAGE_NAMESPACE_URI));
    }

    public void start(PipelineContext pipelineContext) {

        ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        ExternalContext.Response response = externalContext.getResponse();
        try {
            // Read configuration
            Node config = readCacheInputAsDOM4J(pipelineContext, INPUT_CONFIG);

            String imageDirectoryString = XPathUtils.selectStringValueNormalize(config, "/config/image-directory");
            imageDirectoryString = imageDirectoryString.replace('\\', '/');
            // Make sure this ends with a '/' so that it is considered a directory
            if (!imageDirectoryString.endsWith("/"))
                imageDirectoryString = imageDirectoryString + '/';
            URL imageDirectoryURL = URLFactory.createURL(imageDirectoryString);

            String cacheDirectoryString = XPathUtils.selectStringValueNormalize(config, "/config/cache/directory");
            File cacheDir = (cacheDirectoryString == null) ? null : new File(cacheDirectoryString);
            if (cacheDir != null && !cacheDir.isDirectory())
                throw new IllegalArgumentException("Invalid cache directory: " + cacheDirectoryString);

            float defaultQuality = selectFloatValue(config, "/config/default-quality", DEFAULT_QUALITY);
            if (defaultQuality < 0.0f || defaultQuality > 1.0f)
                throw new IllegalArgumentException("default-quality must be comprised between 0.0 and 1.0");

            boolean useSandbox = selectBooleanValue(config, "/config/use-sandbox", DEFAULT_USE_SANDBOX);
            String cachePathEncoding = XPathUtils.selectStringValueNormalize(config, "/config/cache/path-encoding");

            // Read request info
            Node imageConfig = readCacheInputAsDOM4J(pipelineContext, INPUT_IMAGE);

            // Read URL
            String urlString = XPathUtils.selectStringValueNormalize(imageConfig, "/image/url");

            // For backward compatibility, try to get path element (which also contained an URL!)
            if (urlString == null) {
                urlString = XPathUtils.selectStringValueNormalize(imageConfig, "/image/path");
            }

            float quality = selectFloatValue(imageConfig, "/image/quality", defaultQuality);
            boolean useCache = cacheDir != null && selectBooleanValue(imageConfig, "/image/use-cache", DEFAULT_USE_CACHE);
            int transformCount = XPathUtils.selectIntegerValue(imageConfig, "count(/image/transform)").intValue();
            Object transforms = XPathUtils.selectObjectValue(imageConfig, "/image/transform");
            if (transforms != null && transforms instanceof Node)
                transforms = Collections.singletonList(transforms);
            Iterator transformIterator = XPathUtils.selectIterator(imageConfig, "/image/transform");

            URLConnection urlConnection = null;
            InputStream urlConnectionInputStream = null;
            try {
                // Make sure the requested resource exists and is valid
                URL newURL = null;
                try {
                    newURL = URLFactory.createURL(imageDirectoryURL, urlString);
                    // Check if new URL is relative to image directory URL
                    boolean relative = NetUtils.relativeURL(imageDirectoryURL, newURL);
                    if (useSandbox && !relative) {
                        response.setStatus(ExternalContext.SC_NOT_FOUND);
                        return;
                    }
                    // Try to open the connection
                    urlConnection = newURL.openConnection();
                    // Get InputStream and make sure it supprorts marks
                    urlConnectionInputStream = urlConnection.getInputStream();
                    if (!urlConnectionInputStream.markSupported())
                        urlConnectionInputStream = new BufferedInputStream(urlConnectionInputStream);
                    // Make sure the resource looks like a JPEG file
                    String contentType = URLConnection.guessContentTypeFromStream(urlConnectionInputStream);
                    if (!"image/jpeg".equals(contentType)) {
                        response.setStatus(ExternalContext.SC_NOT_FOUND);
                        return;
                    }
                } catch (IOException e) {
                    response.setStatus(ExternalContext.SC_NOT_FOUND);
                    return;
                }

                // Get date of last modification of resource
                long lastModified = NetUtils.getLastModified(urlConnection);

                // Cache handling
                String cacheFileName = useCache ? computeCacheFileName(cachePathEncoding, urlString, (List) transforms) : null;
                File cacheFile = useCache ? new File(cacheDir, cacheFileName) : null;
                boolean cacheInvalid = !useCache || !cacheFile.exists() || lastModified == 0 || lastModified > cacheFile.lastModified() || cacheFile.length() == 0;
                boolean mustProcess = cacheInvalid;
                boolean updateCache = useCache && cacheInvalid;

                // Set Last-Modified, required for caching and conditional get
                response.setCaching(lastModified, false, false);

                // Check If-Modified-Since and don't return content if condition is met
                if ((transformCount == 0 || !mustProcess) && !response.checkIfModifiedSince(lastModified, false)) {
                    response.setStatus(ExternalContext.SC_NOT_MODIFIED);
                    return;
                }

                // Set Content-Type
                response.setContentType("image/jpeg");

                // Optimize if no transformation is specified
                if (transformCount == 0) {
                    NetUtils.copyStream(urlConnectionInputStream, response.getOutputStream());
                    return;
                }

                // Process image if needed
                if (mustProcess) {
                    boolean closeOutputStream = false;
                    OutputStream os = null;
                    try {
                        // Try to obtain decoded image from cache first
                        Long cacheValidity = new Long(lastModified);
                        String cacheKey = "[" + newURL.toExternalForm() + "][" + cacheValidity + "]";
                        BufferedImage img1 = null;
                        // Decode one image at a time to try to minimize the memory impact
                        // NOTE: This should probably be configurable
                        synchronized (ImageServer.this) {
                            img1 = (cache == null) ? null : (BufferedImage) cache.get(cacheKey);
                            // If this failed (most common case) decode the image
                            if (img1 == null) {
                                // Decode image into BufferedImage
                                JPEGImageDecoder decoder = JPEGCodec.createJPEGDecoder(urlConnectionInputStream);
                                img1 = decoder.decodeAsBufferedImage();

                                // Store the image into the soft cache
                                if (cache == null)
                                    cache = new SoftCacheImpl(0);
                                cache.put(cacheKey, img1);
                            } else {
                                cache.refresh(cacheKey);
                                //logger.info("Found image in cache with key: " + cacheKey);
                                logger.info("Found decoded image in cache");
                            }
                        }

                        // Filter image
                        BufferedImage img2 = filter(img1, transformIterator);

                        // Create OutputStream
                        if (updateCache) {
                            File outputDir = cacheFile.getParentFile();
                            if (!outputDir.exists() && !outputDir.mkdirs()) {
                                logger.info("Cannot create cache directory: " + outputDir.getCanonicalPath());
                                response.setStatus(ExternalContext.SC_INTERNAL_SERVER_ERROR);
                                return;
                            }
                            os = new FileOutputStream(cacheFile);
                            closeOutputStream = true;
                        } else {
                            os = response.getOutputStream();
                        }

                        // Encode image to OutputStream
                        JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(os);
                        JPEGEncodeParam params = encoder.getDefaultJPEGEncodeParam(img2);
                        params.setQuality(quality, false);
                        encoder.setJPEGEncodeParam(params);
                        encoder.encode(img2);
                    } catch (OXFException e) {
                        logger.error(OXFException.getRootThrowable(e));
                        response.setStatus(ExternalContext.SC_INTERNAL_SERVER_ERROR);
                        return;
                    } finally {
                        if (os != null && closeOutputStream) os.close();
                    }
                }

                // Send cached image if relevant
                if (useCache) {
                    InputStream is = new FileInputStream(cacheFile);
                    OutputStream os = response.getOutputStream();
                    try {
                        NetUtils.copyStream(is, os);
                    } finally {
                        is.close();
                    }
                }
            } finally {
                // Make sure the connection is closed because when getting the
                // last modified date, the stream is actually opened. When using
                // the file: protocol, the file can be locked on disk.
                if (urlConnection != null && "file".equalsIgnoreCase(urlConnection.getURL().getProtocol())) {
                    if (urlConnectionInputStream != null) urlConnectionInputStream.close();
                }
            }

        } catch (OutOfMemoryError e) {
            logger.info("Ran out of memory while processing image");
            throw e;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private String computeCacheFileName(String type, String path, List nodes) {
        // Create digest document and digest
        Document document = new NonLazyUserDataDocument();
        Element rootElement = document.addElement("image");
        for (Iterator i = nodes.iterator(); i.hasNext();) {
            Element element = (Element) i.next();
            rootElement.add(element.createCopy());
        }
        String digest = NumberUtils.toHexString(Dom4jUtils.getDigest(document));

        // Create file name
        if ("flat".equals(type))
            return computePathNameFlat(path) + "-" + digest;
        else
            return computePathNameHierarchical(path) + "-" + digest;
    }

    private String computePathNameHierarchical(String path) {
        StringTokenizer st = new StringTokenizer(path, "/\\:");
        StringBuffer sb = new StringBuffer();
        while (st.hasMoreElements()) {
            if (sb.length() > 0)
                sb.append(File.separatorChar);
            try {
                sb.append(URLEncoder.encode(st.nextToken(), "utf-8").replace('+', ' '));
            } catch (UnsupportedEncodingException e) {
                throw new OXFException(e);
            }
        }
        return sb.toString();
    }

    private String computePathNameFlat(String path) {
        try {
            return URLEncoder.encode(path, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e);
        }
    }

    private synchronized BufferedImage filter(BufferedImage img, Iterator transformIterator) {
        // Copy the image to RGB if necessary (is there another way? Otherwise some images fail)
        BufferedImage srcImage = img;
        if (img.getType() != BufferedImage.TYPE_INT_RGB) {
            srcImage = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = srcImage.createGraphics();
            graphics.drawImage(img, null, 0, 0);
            graphics.dispose();
        }

        ImageProducer producer = srcImage.getSource();
        int currentWidth = img.getWidth(null);
        int currentHeight = img.getHeight(null);

        // There may be one drawing operation
        List drawConfiguration = new ArrayList();

        // Iterate through all transforms
        while (transformIterator.hasNext()) {
            Node node = (Node) transformIterator.next();
            String transformType = XPathUtils.selectStringValueNormalize(node, "@type");
            if ("scale".equals(transformType)) {
                // Scale image
                String qualityString = XPathUtils.selectStringValueNormalize(node, "quality");
                boolean lowQuality = "low".equals(qualityString);
                boolean scaleUp = selectBooleanValue(node, "scale-up", DEFAULT_SCALE_UP);
                String widthString = XPathUtils.selectStringValueNormalize(node, "width");
                int width;
                int height;
                if (widthString == null) {
                    // There must be a maximum, use it to compute width and height
                    String maxSizeString = XPathUtils.selectStringValueNormalize(node, "max-size");
                    String maxWidthString = XPathUtils.selectStringValueNormalize(node, "max-width");
                    String maxHeightString = XPathUtils.selectStringValueNormalize(node, "max-height");
                    if (maxSizeString != null) {
                        int maxSize = Integer.parseInt(maxSizeString);
                        double scale = (currentWidth > currentHeight)
                                ? ((double) maxSize / (double) currentWidth)
                                : ((double) maxSize / (double) currentHeight);
                        width = (int) (scale * currentWidth);
                        height = (int) (scale * currentHeight);
                    } else if (maxWidthString != null) {
                        int maxWidth = Integer.parseInt(maxWidthString);
                        double scale = (double) maxWidth / (double) currentWidth;
                        width = (int) (scale * currentWidth);
                        height = (int) (scale * currentHeight);
                    } else {
                        int maxHeight = Integer.parseInt(maxHeightString);
                        double scale = (double) maxHeight / (double) currentHeight;
                        width = (int) (scale * currentWidth);
                        height = (int) (scale * currentHeight);
                    }
                } else {
                    // Width and height are specified directly
                    String heightString = XPathUtils.selectStringValueNormalize(node, "height");
                    width = Integer.parseInt(widthString);
                    height = Integer.parseInt(heightString);
                }
                // Make sure we don't scale up if not allowed to
                if (!scaleUp && (width > currentWidth || height > currentHeight)) {
                    width = currentWidth;
                    height = currentHeight;
                }
                // Chain filter if needed
                if (currentWidth != width || currentHeight != height) {
                    ImageFilter scaleFilter = lowQuality ? new ReplicateScaleFilter(width, height) : new AreaAveragingScaleFilter(width, height);
                    producer = new FilteredImageSource(producer, scaleFilter);
                    // Remember current width and height
                    currentWidth = width;
                    currentHeight = height;
                }
            } else if ("crop".equals(transformType)) {
                // Crop image
                int x = selectIntValue(node, "x", 0);
                int y = selectIntValue(node, "y", 0);
                int width = selectIntValue(node, "width", currentWidth - x);
                int height = selectIntValue(node, "height", currentHeight - y);
                // Calculate actual size
                Rectangle2D rect = new Rectangle(x, y, width, height);
                Rectangle2D imageRect = new Rectangle(0, 0, currentWidth, currentHeight);
                Rectangle2D intersection = rect.createIntersection(imageRect);

                // Make sure image is not empty
                if (intersection.getWidth() < 0 || intersection.getHeight() < 0) {
                    logger.info("Resulting image is empty after crop!");
                    throw new OXFException("Resulting image is empty after crop!");
                }

                // Chain filter if needed
                if (!imageRect.equals(intersection)) {
                    ImageFilter cropFilter = new CropImageFilter((int) intersection.getX(),
                            (int) intersection.getY(), (int) intersection.getWidth(), (int) intersection.getHeight());
                    producer = new FilteredImageSource(producer, cropFilter);
                    // Remember current width and height
                    currentWidth = (int) intersection.getWidth();
                    currentHeight = (int) intersection.getHeight();
                }
            } else if ("draw".equals(transformType)) {
                // Don't do anything for now, this must be the last step
                drawConfiguration.add(node);
            }
        }

        Image filteredImg = Toolkit.getDefaultToolkit().createImage(producer);

        // Create resulting image
        BufferedImage newImage = new BufferedImage(currentWidth, currentHeight, srcImage.getType());
        Graphics2D graphics = newImage.createGraphics();
        graphics.drawImage(filteredImg, null, null);
        // Check for drawing operation
        for (Iterator drawConfigIterator = drawConfiguration.iterator(); drawConfigIterator.hasNext();) {
            Node drawConfigNode = (Node) drawConfigIterator.next();
            for (Iterator i = XPathUtils.selectIterator(drawConfigNode, "rect | fill | line"); i.hasNext();) {
                Node node = (Node) i.next();
                String operation = XPathUtils.selectStringValueNormalize(node, "name()");
                if ("rect".equals(operation)) {
                    int x = XPathUtils.selectIntegerValue(node, "@x").intValue();
                    int y = XPathUtils.selectIntegerValue(node, "@y").intValue();
                    int width = XPathUtils.selectIntegerValue(node, "@width").intValue() - 1;
                    int height = XPathUtils.selectIntegerValue(node, "@height").intValue() - 1;
                    Node colorNode = XPathUtils.selectSingleNode(node, "color");
                    if (colorNode != null) {
                        graphics.setColor(getColor(colorNode));
                    }
                    graphics.drawRect(x, y, width, height);
                } else if ("fill".equals(operation)) {
                    int x = XPathUtils.selectIntegerValue(node, "@x").intValue();
                    int y = XPathUtils.selectIntegerValue(node, "@y").intValue();
                    int width = XPathUtils.selectIntegerValue(node, "@width").intValue();
                    int height = XPathUtils.selectIntegerValue(node, "@height").intValue();
                    Node colorNode = XPathUtils.selectSingleNode(node, "color");
                    if (colorNode != null) {
                        graphics.setColor(getColor(colorNode));
                    }
                    graphics.fillRect(x, y, width, height);
                } else if ("line".equals(operation)) {
                    int x1 = XPathUtils.selectIntegerValue(node, "@x1").intValue();
                    int y1 = XPathUtils.selectIntegerValue(node, "@y1").intValue();
                    int x2 = XPathUtils.selectIntegerValue(node, "@x2").intValue();
                    int y2 = XPathUtils.selectIntegerValue(node, "@y2").intValue();
                    Node colorNode = XPathUtils.selectSingleNode(node, "color");
                    if (colorNode != null) {
                        graphics.setColor(getColor(colorNode));
                    }
                    graphics.drawLine(x1, y1, x2, y2);
                }
            }
        }
        graphics.dispose();

        return newImage;
    }

    private Color getColor(Node colorNode) {
        String rgb = XPathUtils.selectStringValueNormalize(colorNode, "@rgb");
        String alpha = XPathUtils.selectStringValueNormalize(colorNode, "@alpha");
        Color color = null;
        if (rgb != null) {
            try {
                color = new Color(Integer.parseInt(rgb.substring(1), 16));
            } catch (NumberFormatException e) {
                throw new OXFException("Can't parse RGB color: " + rgb, e);
            }
        }
        if (color != null && alpha != null) {
            try {
                color = new Color(color.getRed(), color.getGreen(), color.getBlue(), Integer.parseInt(alpha, 16));
            } catch (NumberFormatException e) {
                throw new OXFException("Can't parse alpha color: " + alpha, e);
            }
        }
        return color;
    }

    private boolean selectBooleanValue(Node node, String expr, boolean def) {
        String defaultString = def ? "false" : "true";
        return !defaultString.equals(XPathUtils.selectStringValueNormalize(node, expr));
    }

    private float selectFloatValue(Node node, String expr, float def) {
        String stringValue = XPathUtils.selectStringValueNormalize(node, expr);
        return (stringValue == null) ? def : Float.parseFloat(stringValue);
    }

    private int selectIntValue(Node node, String expr, int def) {
        Integer integerValue = XPathUtils.selectIntegerValue(node, expr);
        return (integerValue == null) ? def : integerValue.intValue();
    }
}
