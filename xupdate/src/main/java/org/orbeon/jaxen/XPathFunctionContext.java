/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/XPathFunctionContext.java,v 1.28 2006/02/05 21:47:41 elharo Exp $
 * $Revision: 1.28 $
 * $Date: 2006/02/05 21:47:41 $
 *
 * ====================================================================
 *
 * Copyright 2000-2002 bob mcwhirter & James Strachan.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   * Neither the name of the Jaxen Project nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Jaxen Project and was originally
 * created by bob mcwhirter <bob@werken.com> and
 * James Strachan <jstrachan@apache.org>.  For more information on the
 * Jaxen Project, please see <http://www.jaxen.org/>.
 *
 * $Id: XPathFunctionContext.java,v 1.28 2006/02/05 21:47:41 elharo Exp $
 */


package org.orbeon.jaxen;

import org.orbeon.jaxen.function.*;
import org.orbeon.jaxen.function.ext.*;
import org.orbeon.jaxen.function.xslt.DocumentFunction;

/** A <code>FunctionContext</code> implementing the core XPath
 *  function library, plus Jaxen extensions.
 *
 *  <p>
 *  The core XPath function library is provided through this
 *  implementation of <code>FunctionContext</code>.  Additionally,
 *  extension functions have been provided, as enumerated below.
 *  </p>
 *
 *  <p>
 *  This class is re-entrant and thread-safe.  If using the
 *  default instance, it is inadvisable to call
 *  {@link #registerFunction(String, String, Function)}
 *  as that will extend the global function context, affecting other
 *  users.  But that's your call, really, now isn't
 *  it?  That may be what you really want to do.
 *  </p>
 *
 *  <p>
 *  Extension functions:
 *  </p>
 *
 *  <ul>
 *     <li>matrix-concat(..)</li>
 *     <li>evaluate(..)</li>
 *     <li>upper-case(..)</li>
 *     <li>lower-case(..)</li>
 *     <li>ends-with(..)</li>
 *  </ul>
 *
 *  @see FunctionContext
 *  @see org.orbeon.jaxen.function
 *  @see org.orbeon.jaxen.function.xslt
 *  @see org.orbeon.jaxen.function.ext
 *
 *  @author <a href="mailto:bob@werken.com">bob mcwhirter</a>
 */
public class XPathFunctionContext extends SimpleFunctionContext
{
    private static XPathFunctionContext instance = new XPathFunctionContext();

    /** Retrieve the default function context
     *
     *  @return the default function context
     */
    public static FunctionContext getInstance()
    {
        return instance;
    }

    /** Create a new XPath function context.
     *  All core XPath and Jaxen extension functions are registered.
     */
    public XPathFunctionContext()
    {
        this(true);
    }

    /** Create a new XPath function context.
     *  All core XPath functions are registered.
     *
     * @param includeExtensionFunctions if true extension functions are included;
     *     if false, they aren't.
     */
    public XPathFunctionContext(boolean includeExtensionFunctions)
    {
        registerXPathFunctions();
        if (includeExtensionFunctions) {
            registerXSLTFunctions();
            registerExtensionFunctions();
        }
    }

    private void registerXPathFunctions() {

        registerFunction( null,  // namespace URI
                          "boolean",
                          new BooleanFunction() );

        registerFunction( null,  // namespace URI
                          "ceiling",
                          new CeilingFunction() );

        registerFunction( null,  // namespace URI
                          "concat",
                          new ConcatFunction() );

        registerFunction( null,  // namespace URI
                          "contains",
                          new ContainsFunction() );

        registerFunction( null,  // namespace URI
                          "count",
                          new CountFunction() );

        registerFunction( null,  // namespace URI
                          "false",
                          new FalseFunction() );

        registerFunction( null,  // namespace URI
                          "floor",
                          new FloorFunction() );

        registerFunction( null,  // namespace URI
                          "id",
                          new IdFunction() );

        registerFunction( null,  // namespace URI
                          "lang",
                          new LangFunction() );

        registerFunction( null,  // namespace URI
                          "last",
                          new LastFunction() );

        registerFunction( null,  // namespace URI
                          "local-name",
                          new LocalNameFunction() );

        registerFunction( null,  // namespace URI
                          "name",
                          new NameFunction() );

        registerFunction( null,  // namespace URI
                          "namespace-uri",
                          new NamespaceUriFunction() );

        registerFunction( null,  // namespace URI
                          "normalize-space",
                          new NormalizeSpaceFunction() );

        registerFunction( null,  // namespace URI
                          "not",
                          new NotFunction() );

        registerFunction( null,  // namespace URI
                          "number",
                          new NumberFunction() );

        registerFunction( null,  // namespace URI
                          "position",
                          new PositionFunction() );

        registerFunction( null,  // namespace URI
                          "round",
                          new RoundFunction() );

        registerFunction( null,  // namespace URI
                          "starts-with",
                          new StartsWithFunction() );

        registerFunction( null,  // namespace URI
                          "string",
                          new StringFunction() );

        registerFunction( null,  // namespace URI
                          "string-length",
                          new StringLengthFunction() );

        registerFunction( null,  // namespace URI
                          "substring-after",
                          new SubstringAfterFunction() );

        registerFunction( null,  // namespace URI
                          "substring-before",
                          new SubstringBeforeFunction() );

        registerFunction( null,  // namespace URI
                          "substring",
                          new SubstringFunction() );

        registerFunction( null,  // namespace URI
                          "sum",
                          new SumFunction() );

        registerFunction( null,  // namespace URI
                          "true",
                          new TrueFunction() );

        registerFunction( null,  // namespace URI
                          "translate",
                          new TranslateFunction() );
    }

    private void registerXSLTFunctions() {

        // extension functions defined in XSLT
        registerFunction( null,  // namespace URI
                          "document",
                          new DocumentFunction() );
    }

    private void registerExtensionFunctions() {
        // extension functions should go into a namespace, but which one?
        // for now, keep them in default namespace to not break any code

        registerFunction( null,  // namespace URI
                          "matrix-concat",
                          new MatrixConcatFunction() );

        registerFunction( null,  // namespace URI
                          "evaluate",
                          new EvaluateFunction() );

        registerFunction( null,  // namespace URI
                          "lower-case",
                          new LowerFunction() );

        registerFunction( null,  // namespace URI
                          "upper-case",
                          new UpperFunction() );

        registerFunction( null,  // namespace URI
                          "ends-with",
                          new EndsWithFunction() );
    }


}
