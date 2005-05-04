/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
/*
 * @(#)$Id: PathMatcher.java,v 1.1 2005/05/04 23:55:58 ebruchez Exp $
 *
 * Copyright 2001 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * This software is the proprietary information of Sun Microsystems, Inc.  
 * Use is subject to license terms.
 * 
 */
package org.orbeon.oxf.xforms.msv;

import com.sun.msv.grammar.xmlschema.XPath;
import org.relaxng.datatype.Datatype;

/**
 * Base implementation of XPath matching engine.
 * 
 * It only supports the subset defined in XML Schema Part 1. Extra care
 * must be taken to call the testInitialMatch method after the creation of an object.
 * 
 * Match to an attribute is not supported. It is implemented in FieldPathMatcher
 * class.
 * 
 * The onMatched method is called when the specified XPath matches the current element.
 * Derived classes should implement this method to do something useful.
 * 
 * @author <a href="mailto:kohsuke.kawaguchi@eng.sun.com">Kohsuke KAWAGUCHI</a>
 */
public abstract class PathMatcher extends MatcherBundle {
    
    protected PathMatcher( IDConstraintChecker owner, XPath[] paths ) {
        super(owner);
        children = new Matcher[paths.length];
        for( int i=0; i<paths.length; i++ )
            children[i] = new SinglePathMatcher(paths[i]);
        // if there is a empty XPath ("."), then matchFound is set
        // in the above constructor.
    }
    
    /**
     * this method should be called immediately after the installment of this PathMatcher.
     */
    protected void start( final org.dom4j.Element elt ) {
        if(matchFound)
            onElementMatched( elt );
        matchFound = false;
    }
    
    /**
     * this method is called when the element matches the XPath.
     */
    protected abstract void onElementMatched( final org.dom4j.Element elt ) ;

    /**
     * this method is called when the attribute matches the XPath.
     */
    protected abstract void onAttributeMatched( final org.dom4j.Attribute att , Datatype type );

    
    
    
    protected void startElement( final org.dom4j.Element elt ) {
        super.startElement( elt );
        if(matchFound)
            onElementMatched( elt );
        matchFound = false;
    }
    
    protected void onAttribute( final org.dom4j.Attribute att, Datatype type )  {
        super.onAttribute( att, type );
        if(matchFound)
            onAttributeMatched( att, type );
        matchFound = false;
    }
    
    
    /**
     * a flag that indicates that this element/attribute matches the path expression.
     * This flag is set by one of the child SinglePathMatcher.
     */
    private boolean matchFound = false;
    
    
    /**
     * the XPath matching engine.
     * 
     * <p>
     * This class implements the matching engine for single
     * <a href="http://www.w3.org/TR/xmlschema-1/#Path">
     * "Path"</a>.
     * 
     * <p>
     * The outer <code>PathMatcher</code> uses multiple instances of this class
     * and thereby implements the matching engine for the whole "Selector".
     * 
     * <p>
     * This class only supports the subset defined in XML Schema Part 1. Extra care
     * must be taken to call the testInitialMatch method
     * after the creation of an object.
     * 
     * <p>
     * When a match is found, this class notifies the parent object by using a flag.
     * 
     * 
     */
    private class SinglePathMatcher extends Matcher {
        /**
         * stores matched steps.
         * first dimension is expanded as the depth goes deep.
         * second dimension is always equal to the size of steps.
         */
        private boolean[][]            activeSteps;
//        private short                currentDepth=0;
        protected final XPath        path;
    
        /**
         * this flag is set to true when the path contains an attribute step
         * and the current element matches the element part of the path.
         * 
         * When this flag is true, we need to honor the onAttribute event
         * and check if the path really matches to the attribute.
         */
        private boolean                elementMatched = false;
    
        protected SinglePathMatcher( XPath path ) {
            super(PathMatcher.this.owner);
            this.path = path;
            activeSteps = new boolean[4][];
            /*
                activeSteps[i][0] is used to represent an implicit "root".
                For example, when XPath is "//A/B",
            
                    [0]:root        [1]:A        [2]:B
                
                (initial state)
                        1            0            0        (1 indicates "active")
                                    [0] is initialized to 1.
                
                (startElement(X))
                (step:1 shift to right)
                        1(*1)        1            0
                                *1    [0] will be populated by isAnyDescendant field.
                                    In this case, since isAnyDescendant ("//") is true,
                                    [0] is set to true after shift. This indicates that
                                    new element X can possibly be used as the implicit root.
                (step:2 perform name test)
                        1            0            0
                                    root is excluded from the test. Since A doesn't match
                                    X, the corresponding field is set to false.
                
                (startElement(A))
                (step:1 shift to right)
                        1            1            0
                (step:2 perform name test)
                        1            1            0
                
                (startElement(B))
                (step:1 shift to right)
                        1            1            1
                (step:2 perform name test)
                        1            0            1 (*2)
                                *2    Now that the right most slot is true,
                                    this element B matches XPath.
            */
            activeSteps[0] = new boolean[path.steps.length+1];
            activeSteps[0][0] = true;    // initialization
            // we only need an empty buffer for activeStep[0].
            // other slots are filled on demand.
            
            if(path.steps.length==0) {
                // if the step is length 0, (that is, ".")
                // it is an immediate match.
                
                if( path.attributeStep==null )
                    // report to the parent PathMatcher that a match was found
                    matchFound = true;
                else
                    elementMatched = true;
            }
            
        }
    
        protected void startElement( final org.dom4j.Element elt ) {
            elementMatched = false;    // reset the flag.
            
            final int depth = getDepth();
            
            if(depth==activeSteps.length-1) {
                // if the buffer is used up, expand buffer
                boolean[][] newBuf = new boolean[depth*2][];
                System.arraycopy( activeSteps, 0, newBuf, 0, activeSteps.length );
                activeSteps = newBuf;
            }
//            currentDepth++;
            int len = path.steps.length;
            
            boolean[] prvBuf = activeSteps[depth-1];
            boolean[] curBuf = activeSteps[depth];
            if(curBuf==null)    activeSteps[depth]=curBuf=new boolean[len+1/*implicit root*/];
            
            // shift to right
            if(len!=0) {
                System.arraycopy(
                    prvBuf, 0, curBuf, 1, len );
                curBuf[0] = path.isAnyDescendant;
            }
            
            // perform name test and deactivate unmatched steps
            final String nsURI = elt.getNamespaceURI();
            final String lnam = elt.getName();
            for( int i=1; i<=len; i++ )
                // exclude root from test.
                if( curBuf[i] && !path.steps[i-1].accepts( nsURI, lnam ) )
                    curBuf[i] = false;
            
            if( curBuf[len] ) {
                // this element matched this path
                if( path.attributeStep==null )
                    // report to the parent PathMatcher that a match was found
                    matchFound = true;
                else
                    elementMatched = true;
            }
        }
    
        protected void onAttribute( final org.dom4j.Attribute att, Datatype type )  {
            // attribute step is not tested when the parent element doesn't match
            // the parent XPath expression.
            if( !elementMatched )    return;
            
            final String nsURI = att.getNamespaceURI();
            final String lnam = att.getName();
            if( path.attributeStep.accepts( nsURI, lnam ) )
                // report to the parent PathMatcher that a match was found
                matchFound = true;
            // keep the elementMatched flag as-is.
        }
    
        protected void endElement( Datatype dt ) {
            elementMatched = false;    // reset the flag.
//            currentDepth--;
        }
    }
}
