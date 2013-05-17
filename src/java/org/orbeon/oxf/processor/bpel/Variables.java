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
package org.orbeon.oxf.processor.bpel;

import org.orbeon.oxf.common.OXFException;

import java.util.*;

public class Variables implements Cloneable {

    private Map variableToParts = new HashMap();
    private String inputVariable;
    private String outputVariable;

    public void declareVariable(String variable) {
        variableToParts.put(variable,  new HashMap());
    }

    public void setInputVariable(String variable) {
        this.inputVariable = variable;
    }

    public String getInputVariable() {
        return inputVariable;
    }

    public String getOutputVariable() {
        return outputVariable;
    }

    public void setOutputVariable(String outputVariable) {
        this.outputVariable = outputVariable;
    }

    public String getCurrentIdForVariablePart(String variable, String part) {
        int count = getCurrentCountForVariablePart(variable,  part);
        return variable.equals(inputVariable) && count == 1
                ? part
                : buildId(variable, part, getCurrentCountForVariablePart(variable,  part));
    }

    public String getNewIdForVariablePart(String variable, String part) {
        Map parts = getParts(variable);
        Integer count = (Integer) parts.get(part);
        if (count == null) {
            count = new Integer(1);
        } else {
            count = new Integer(count.intValue() + 1);
        }
        parts.put(part, count);
        return buildId(variable, part, count.intValue());
    }

    /**
     * @return The union of this variables declared in the current object with
     * the variables declared in the other object. When a variable is declared
     * in both, its id in the returned object is the maximum of the two ids.
     */
    public Variables max(Variables other) {
        Variables result = new Variables();
        result.inputVariable = other.inputVariable;
        result.outputVariable = other.outputVariable;

        // Declare all variables from this and other
        for (Iterator i = this.variableToParts.keySet().iterator(); i.hasNext();)
            result.declareVariable((String) i.next());
        for (Iterator i = other.variableToParts.keySet().iterator(); i.hasNext();)
            result.declareVariable((String) i.next());

        // Create iterators
        Iterator thisIterator = this.iterateVariablesParts();
        Iterator otherIterator = other.iterateVariablesParts();
        VariablePart thisCurrent = thisIterator.hasNext() ? (VariablePart) thisIterator.next() : null;
        VariablePart otherCurrent = otherIterator.hasNext() ? (VariablePart) otherIterator.next() : null;

        while (thisCurrent != null || otherCurrent != null) {
            if (thisCurrent != null && otherCurrent != null
                    && thisCurrent.getVariable().equals(otherCurrent.getVariable())
                    && thisCurrent.getPart().equals(otherCurrent.getPart())) {
                // Add the one with the highest count
                int thisCount = this.getCurrentCountForVariablePart(thisCurrent.getVariable(), thisCurrent.getPart());
                int otherCount = other.getCurrentCountForVariablePart(otherCurrent.getVariable(), otherCurrent.getPart());
                result.setVariablePart(thisCurrent.getVariable(), thisCurrent.getPart(),
                        Math.max(thisCount, otherCount));
                thisCurrent = thisIterator.hasNext() ? (VariablePart) thisIterator.next() : null;
                otherCurrent = otherIterator.hasNext() ? (VariablePart) otherIterator.next() : null;
            } else if (otherCurrent == null || (thisCurrent != null &&
                    (thisCurrent.getVariable().compareTo(otherCurrent.getVariable()) <= 0
                    || thisCurrent.getVariable().compareTo(otherCurrent.getVariable()) < 0))) {
                // Add thisCurrent
                result.setVariablePart(thisCurrent.getVariable(), thisCurrent.getPart(),
                        getCurrentCountForVariablePart(thisCurrent.getVariable(), thisCurrent.getPart()));
                thisCurrent = thisIterator.hasNext() ? (VariablePart) thisIterator.next() : null;
            } else {
                // Add otherCurrent
                result.setVariablePart(otherCurrent.getVariable(), otherCurrent.getPart(),
                        other.getCurrentCountForVariablePart(otherCurrent.getVariable(), otherCurrent.getPart()));
                otherCurrent = otherIterator.hasNext() ? (VariablePart) otherIterator.next() : null;
            }
        }

        return result;
    }

    /**
     * @return A new object with all the variables declared in this object that
     * are not declared in the other object, or that have a higher id in the
     * this object.
     *
     * Other shall not contain any variable/part not contained in this.
     */
    public Variables minus(Variables other) {
        Variables result = new Variables();
        result.inputVariable = inputVariable;
        result.outputVariable = outputVariable;
        Iterator thisIterator = this.iterateVariablesParts();
        Iterator otherIterator = other.iterateVariablesParts();
        VariablePart thisCurrent = thisIterator.hasNext() ? (VariablePart) thisIterator.next() : null;
        VariablePart otherCurrent = otherIterator.hasNext() ? (VariablePart) otherIterator.next() : null;

        while (thisCurrent != null || otherCurrent != null) {
            if (thisCurrent != null && otherCurrent != null
                    && thisCurrent.getVariable().equals(otherCurrent.getVariable())
                    && thisCurrent.getPart().equals(otherCurrent.getPart())) {
                // Add if this higher than other
                int thisCount = this.getCurrentCountForVariablePart(thisCurrent.getVariable(), thisCurrent.getPart());
                int otherCount = other.getCurrentCountForVariablePart(otherCurrent.getVariable(), otherCurrent.getPart());
                if (thisCount > otherCount)
                    result.setVariablePart(thisCurrent.getVariable(), thisCurrent.getPart(), thisCount);
                thisCurrent = thisIterator.hasNext() ? (VariablePart) thisIterator.next() : null;
                otherCurrent = otherIterator.hasNext() ? (VariablePart) otherIterator.next() : null;
            } else if (otherCurrent == null || (thisCurrent != null &&
                    (thisCurrent.getVariable().compareTo(otherCurrent.getVariable()) <= 0
                    || thisCurrent.getVariable().compareTo(otherCurrent.getVariable()) < 0))) {
                // Add thisCurrent
                result.setVariablePart(thisCurrent.getVariable(), thisCurrent.getPart(),
                        getCurrentCountForVariablePart(thisCurrent.getVariable(), thisCurrent.getPart()));
                thisCurrent = thisIterator.hasNext() ? (VariablePart) thisIterator.next() : null;
            } else {
                throw new OXFException("Current object does not have a variable '" + otherCurrent.getVariable()
                        + "' part '" + otherCurrent.getPart() + "'");
            }
        }

        return result;
    }

    /**
     * @return An iterator over instance of VariablePart. The VariablePart are
     * returned ordered by variable/part.
     */
    public Iterator iterateVariablesParts() {
        return new Iterator() {

            private String currentVariable;
            private Iterator variablesIterator;
            private Iterator partsIterator;

            {
                List variablesNames = new ArrayList(variableToParts.keySet());
                Collections.sort(variablesNames);
                variablesIterator = variablesNames.iterator();
                nextVariable();
            }

            public boolean hasNext() {
                return partsIterator != null;
            }

            public Object next() {
                VariablePart result = new VariablePart(currentVariable, (String) partsIterator.next());
                if (! partsIterator.hasNext())
                    nextVariable();
                return result;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

            private void nextVariable() {
                while (true) {
                    if (! variablesIterator.hasNext()) {
                        partsIterator = null;
                        break;
                    }
                    currentVariable = (String) variablesIterator.next();
                    Map parts = getParts(currentVariable);
                    if (!parts.isEmpty()) {
                        List partsNames = new ArrayList(parts.keySet());
                        Collections.sort(partsNames);
                        partsIterator = partsNames.iterator();
                        break;
                    }
                }
            }
        };
    }

    /**
     * Make this object an alias to the <code>variables</code> parameter. Any
     * modification to <code>variables</code> will impact this object and
     * vice-versa.
     */
    public void alias(Variables variables) {
        this.variableToParts = variables.variableToParts;
        this.inputVariable = variables.inputVariable;
        this.outputVariable = variables.outputVariable;
    }

    public Object clone() {
        Map newVariableToParts = new HashMap();
        for (Iterator i = variableToParts.keySet().iterator(); i.hasNext();) {
            Object key = i.next();
            Map parts = (Map) variableToParts.get(key);
            Map newParts = new HashMap();
            for (Iterator j = parts.keySet().iterator(); j.hasNext();) {
                Object part = j.next();
                newParts.put(part, parts.get(part));
            }
            newVariableToParts.put(key, newParts);
        }
        Variables newVariables = new Variables();
        newVariables.variableToParts = newVariableToParts;
        newVariables.inputVariable = inputVariable;
        newVariables.outputVariable = outputVariable;
        return newVariables;
    }

    private int getCurrentCountForVariablePart(String variable, String part) {
        Map parts = getParts(variable);
        Integer count = (Integer) parts.get(part);
        if (count == null) {
            if (variable.equals(inputVariable)) {
                getNewIdForVariablePart(variable, part);
                return getCurrentCountForVariablePart(variable, part);
            } else {
                throw new OXFException("Part '" + part + "' of variable '" + variable + "' not initialized");
            }
        }
        return count.intValue();
    }

    private Map getParts(String variable) {
        Map parts = (Map) variableToParts.get(variable);
        if (parts == null)
            throw new OXFException("Variable '" + variable + "' not declared");
        return parts;
    }

    private void setVariablePart(String variable, String part, int count) {
        if (! variableToParts.containsKey(variable))
            variableToParts.put(variable, new HashMap());
        Map parts = (Map) variableToParts.get(variable);
        parts.put(part, new Integer(count));
    }

    public class VariablePart {
        private String variable;
        private String part;

        public VariablePart(String variable, String part) {
            this.variable = variable;
            this.part = part;
        }

        public String getVariable() {
            return variable;
        }

        public String getPart() {
            return part;
        }
    }

    public String buildId(String variable, String part, int count) {
        return variable + "$" + part + "$" + count;
    }

    /**
     * @return String representation for debugging
     */
    public String toString() {
        StringBuffer result = new StringBuffer();
        for (Iterator i = iterateVariablesParts(); i.hasNext();) {
            VariablePart variablePart = (VariablePart) i.next();
            result.append(variablePart.getVariable());
            result.append(", ");
            result.append(variablePart.getPart());
            result.append(" -> ");
            result.append(getCurrentIdForVariablePart(variablePart.getVariable(), variablePart.getPart()));
            result.append("\n");
        }
        return result.toString();
    }
}
