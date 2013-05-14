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
package org.orbeon.oxf.util;

import orbeon.apache.xalan.extensions.XSLProcessorContext;
import orbeon.apache.xalan.templates.ElemExtensionCall;
import org.apache.struts.Globals;
import org.apache.struts.taglib.bean.MessageTag;
import org.apache.struts.taglib.html.JavascriptValidatorTag;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.servlet.ServletExternalContext;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class StrutsUtils {

    private static String nullIfEmpty(String s) {
        return ("".equals(s)) ? null : s;
    }

    public static String messageTag(String key) {
        return messageTag(key, "", "", "" , "", "" , "");
    }

    public static String messageTag(String key, String bundle) {
        return messageTag(key, bundle, "", "" , "", "" , "");
    }

    public static String messageTag(String key, String bundle, String arg0) {
        return messageTag(key, bundle, arg0, "", "" , "", "" );
    }

    public static String messageTag(String key, String bundle, String arg0, String arg1) {
        return messageTag(key, bundle, arg0, arg1, "", "", "" );
    }

    public static String messageTag(String key, String bundle, String arg0, String arg1, String arg2) {
        return messageTag(key, bundle, arg0, arg1, arg2, "", "" );
    }

    public static String messageTag(String key, String bundle, String arg0, String arg1, String arg2, String arg3) {
        return messageTag(key, bundle, arg0, arg1, arg2, arg3, "");
    }

    /**
     * Call the Struts Message tag.
     */
    public static String messageTag(String key, String bundle, String arg0, String arg1, String arg2, String arg3, String arg4) {

        // Normalize parameters
        key = nullIfEmpty(key);

        if (key == null && bundle == null)
            throw new OXFException("Attribute 'key' is mandatory in struts:message");

        bundle = nullIfEmpty(bundle);
        arg0 = nullIfEmpty(arg0);
        arg1 = nullIfEmpty(arg1);
        arg2 = nullIfEmpty(arg2);
        arg3 = nullIfEmpty(arg3);
        arg4 = nullIfEmpty(arg4);

        // Call tag
        OXFPageContext pageContext = getPageContext();
        MessageTag messageTag = new MessageTag();
        try {
            messageTag.setPageContext(pageContext);

            messageTag.setKey(key);
            messageTag.setBundle(bundle);
            messageTag.setArg0(arg0);
            messageTag.setArg1(arg1);
            messageTag.setArg2(arg2);
            messageTag.setArg3(arg3);
            messageTag.setArg4(arg4);

            messageTag.doStartTag();

            return pageContext.getResult();
        } catch (Exception e) {
            throw new OXFException(e);
        } finally {
            messageTag.release();
        }
    }

    public static String javaScriptTag(String formName, String dynamicJavaScript, String staticJavaScript, String method) {
        return javaScriptTag(formName, dynamicJavaScript, staticJavaScript, method, "");
    }

    public static String javaScriptTag(String formName, String dynamicJavaScript, String staticJavaScript) {
        return javaScriptTag(formName, dynamicJavaScript, staticJavaScript, "", "");
    }

    public static String javaScriptTag(String formName, String dynamicJavaScript) {
        return javaScriptTag(formName, dynamicJavaScript, "", "", "");
    }

    public static String javaScriptTag(String formName) {
        return javaScriptTag(formName, "", "", "", "");
    }

    /**
     * Call the Struts JavaScriptValidator tag.
     */
    public static String javaScriptTag(String formName, String dynamicJavaScript, String staticJavaScript, String method, String page) {

        // Normalize parameters
        formName = nullIfEmpty(formName);

        if (formName == null)
            throw new OXFException("Attribute 'formName' is mandatory in struts:javascript");

        dynamicJavaScript = nullIfEmpty(dynamicJavaScript);
        staticJavaScript = nullIfEmpty(staticJavaScript);
        method = nullIfEmpty(method);
        page = nullIfEmpty(page);

        // Call tag
        OXFPageContext pageContext = getPageContext();
        JavascriptValidatorTag js = new JavascriptValidatorTag();
        try {
            js.setCdata("true");
            js.setFormName(formName);
            js.setPageContext(pageContext);

            if (dynamicJavaScript != null)
                js.setDynamicJavascript(dynamicJavaScript );
            if (staticJavaScript != null)
                js.setStaticJavascript(staticJavaScript);
            if (method != null)
                js.setMethod(method);

            try {
                if (page != null) {
                    int i = Integer.parseInt(page);
                    js.setPage(i);
                }
            } catch (NumberFormatException e) {
                throw new OXFException("struts:javascript page attribute must be an integer", e);
            }

            js.doStartTag();
            String result = pageContext.getResult();
            return result.substring(result.indexOf("<![CDATA[") + 9, result.indexOf("]]>"));
        } catch (Exception e) {
            throw new OXFException(e);
        } finally {
            js.release();
        }
    }

    /**
     * Xalan extension method. Use the static methods instead for portability.
     */
    public String message(XSLProcessorContext xslContext, ElemExtensionCall extElem) {
        try {
            String key = extElem.getAttribute("key", xslContext.getContextNode(), xslContext.getTransformer());
            String bundle = extElem.getAttribute("bundle", xslContext.getContextNode(), xslContext.getTransformer());
            String arg0 = extElem.getAttribute("arg0", xslContext.getContextNode(), xslContext.getTransformer());
            String arg1 = extElem.getAttribute("arg1", xslContext.getContextNode(), xslContext.getTransformer());
            String arg2 = extElem.getAttribute("arg2", xslContext.getContextNode(), xslContext.getTransformer());
            String arg3 = extElem.getAttribute("arg3", xslContext.getContextNode(), xslContext.getTransformer());
            String arg4 = extElem.getAttribute("arg4", xslContext.getContextNode(), xslContext.getTransformer());

            return messageTag(key, bundle, arg0, arg1, arg2, arg3, arg4);
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Xalan extension method. Use the static methods instead for portability.
     */
    public String javascript(XSLProcessorContext xslContext, ElemExtensionCall extElem) {
        return javaScript(xslContext, extElem);
    }

    /**
     * Xalan extension method. Use the static methods instead for portability.
     */
    public String javaScript(XSLProcessorContext xslContext, ElemExtensionCall extElem) {
        try {
            String formName = extElem.getAttribute("formName", xslContext.getContextNode(), xslContext.getTransformer());
            String dynamicJavascript = extElem.getAttribute("dynamicJavascript", xslContext.getContextNode(), xslContext.getTransformer());
            String staticJavascript = extElem.getAttribute("staticJavascript", xslContext.getContextNode(), xslContext.getTransformer());
            String method = extElem.getAttribute("method", xslContext.getContextNode(), xslContext.getTransformer());
            String page = extElem.getAttribute("page", xslContext.getContextNode(), xslContext.getTransformer());

            return javaScriptTag(formName, dynamicJavascript, staticJavascript, method, page);
        } catch (TransformerException e) {
            throw new OXFException(e);
        }
    }

    private static OXFPageContext getPageContext() {

        StaticExternalContext.StaticContext staticContext = StaticExternalContext.getStaticContext();
        if (staticContext == null)
            throw new OXFException("Can't find static context for current thread");
        PipelineContext context = staticContext.getPipelineContext();
        if (context == null)
            throw new OXFException("Can't find pipeline context for current thread");

        ExternalContext external = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        if (!(external instanceof ServletExternalContext))
            throw new OXFException("Orbeon Forms is not running as a servlet. This is not supported.");

        ServletContext servletContext = (ServletContext) external.getNativeContext();
        if (servletContext == null)
            throw new OXFException("Can't find servlet context in pipeline context");

        HttpServletRequest request = (HttpServletRequest) external.getNativeRequest();
        if (request == null)
            throw new OXFException("Can't find HTTP Request in pipeline context");

        OXFPageContext pageContext = new OXFPageContext(new StringWriter(), request, servletContext);
        return pageContext;
    }

    public static class OXFPageContext extends PageContext {
        private StringWriter writer;
        private OXFJspWriter jspWriter;
        private HttpServletRequest request;
        private ServletContext context;
        private Map pageContext = new HashMap();

        public OXFPageContext(StringWriter writer, HttpServletRequest request, ServletContext context) {
            this.writer = writer;
            jspWriter = new OXFJspWriter(1024, true);
            jspWriter.setStringWriter(writer);

            this.request = request;
            this.context = context;

            pageContext.put(Globals.XHTML_KEY, "true");
        }

        public String getResult() {
            return writer.toString();
        }

        public Object findAttribute(String s) {
            return null;
        }

        public void forward(String s) throws ServletException, IOException {
        }

        public Object getAttribute(String s) {
            return pageContext.get(s);
        }

        public Object getAttribute(String s, int i) {
            switch (i) {
                case PageContext.APPLICATION_SCOPE:
                    return context.getAttribute(s);
                case PageContext.REQUEST_SCOPE:
                    return request.getAttribute(s);
                case PageContext.SESSION_SCOPE:
                    return request.getSession().getAttribute(s);
                case PageContext.PAGE_SCOPE:
                    return pageContext.get(s);
                default:
                    return null;
            }
        }

        public Enumeration getAttributeNamesInScope(int i) {
            return null;
        }

        public int getAttributesScope(String s) {
            return 0;
        }

        public Exception getException() {
            return null;
        }

        public JspWriter getOut() {
            return jspWriter;
        }

        public Object getPage() {
            return null;
        }

        public ServletRequest getRequest() {
            return request;
        }

        public ServletResponse getResponse() {
            return null;
        }

        public ServletConfig getServletConfig() {
            return null;
        }

        public ServletContext getServletContext() {
            return context;
        }

        public HttpSession getSession() {
            return request.getSession();
        }

        public void handlePageException(Exception e) throws ServletException, IOException {
        }

        public void handlePageException(Throwable throwable) throws ServletException, IOException {
        }

        public void include(String s) throws ServletException, IOException {
        }

        public void initialize(Servlet servlet, ServletRequest servletRequest, ServletResponse servletResponse, String s, boolean b, int i, boolean b1) throws IOException, IllegalStateException, IllegalArgumentException {
        }

        public void release() {
            jspWriter = null;
            request = null;
            context = null;
        }

        public void removeAttribute(String s) {
        }

        public void removeAttribute(String s, int i) {
        }

        public void setAttribute(String s, Object o) {
        }

        public void setAttribute(String s, Object o, int i) {
        }
    }


    public static class OXFJspWriter extends JspWriter {

        private static String NEWLINE = "\n";
        private StringWriter writer;


        public OXFJspWriter(int bufferSize, boolean autoFlush) {
            super(bufferSize, autoFlush);
        }

        public void setStringWriter(StringWriter writer) {
            this.writer = writer;
        }

        public void clear() throws IOException {
        }

        public void clearBuffer() throws IOException {
        }

        public void close() throws IOException {
            writer.close();
        }

        public void flush() throws IOException {
            writer.flush();
        }

        public int getRemaining() {
            return 0;
        }

        public void newLine() throws IOException {
            writer.write(NEWLINE);
        }

        public void print(boolean b) throws IOException {
            writer.write(Boolean.toString(b));
        }

        public void print(char c) throws IOException {
            writer.write(c);
        }

        public void print(char[] chars) throws IOException {
            writer.write(chars);
        }

        public void print(double v) throws IOException {
            writer.write(Double.toString(v));
        }

        public void print(float v) throws IOException {
            writer.write(Float.toString(v));
        }

        public void print(int i) throws IOException {
            writer.write(Integer.toString(i));
        }

        public void print(long l) throws IOException {
            writer.write(Long.toString(l));
        }

        public void print(Object o) throws IOException {
            writer.write(o.toString());
        }

        public void print(String s) throws IOException {
            writer.write(s);
        }

        public void println() throws IOException {
            writer.write(NEWLINE);
        }

        public void println(boolean b) throws IOException {
            writer.write(Boolean.toString(b) + NEWLINE);
        }

        public void println(char c) throws IOException {
            writer.write(c + NEWLINE);
        }

        public void println(char[] chars) throws IOException {
            final String s = new String( chars );
            writer.write( s + NEWLINE);
        }

        public void println(double v) throws IOException {
            writer.write(Double.toString(v) + NEWLINE);
        }

        public void println(float v) throws IOException {
            writer.write(Float.toString(v) + NEWLINE);
        }

        public void println(int i) throws IOException {
            writer.write(Integer.toString(i) + NEWLINE);
        }

        public void println(long l) throws IOException {
            writer.write(Long.toString(l) + NEWLINE);
        }

        public void println(Object o) throws IOException {
            writer.write(o.toString() + NEWLINE);
        }

        public void println(String s) throws IOException {
            writer.write(s + NEWLINE);
        }

        public void write(char cbuf[], int off, int len) throws IOException {
            writer.write(cbuf, off, len);
        }
    }
}
