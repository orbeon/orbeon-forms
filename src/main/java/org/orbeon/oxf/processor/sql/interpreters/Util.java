package org.orbeon.oxf.processor.sql.interpreters;


public class Util {

    /**
     * Convert a double into a String without scientific notation.
     *
     * This is useful for XPath 1.0, which does not understand the scientific notation.
     */
    static String removeScientificNotation(double value) {

        String result = Double.toString(value);
        int eIndex = result.indexOf('E');

        if (eIndex == -1) {
            // No scientific notation, return value as is
            return stripZeros(result);
        } else {
            // Scientific notation, convert value

            // Parse string representation
            String mantissa = result.substring(0, eIndex);
            boolean negative = mantissa.charAt(0) == '-';
            String sign = negative ? "-" : "";
            String mantissa1 = mantissa.substring(negative ? 1 : 0, negative ? 2 : 1);
            String mantissa2 = mantissa.substring(negative ? 3 : 2);
            int exponent = Integer.parseInt(result.substring(eIndex + 1));

            // Calculate result
            if (exponent > 0) {
                // Positive exponent, shift decimal point to the right
                int mantissa2Length = mantissa2.length();
                if (exponent > mantissa2Length) {
                    result = sign + mantissa1 + mantissa2 + nZeros(exponent - mantissa2Length);
                } else if (exponent == mantissa2Length) {
                    result = sign + mantissa1 + mantissa2;
                } else {
                    result = sign + mantissa1 + mantissa2.substring(0, exponent) + '.' + mantissa2.substring(exponent);
                }
            } else if (exponent == 0) {
                // Not sure if this can happen
                result = mantissa;
            } else {
                // Negative exponent, shift decimal point to the left
                result = sign + '0' + '.' + nZeros(-exponent - 1) + mantissa1 + mantissa2;
            }
            return stripZeros(result);
        }
    }

    /**
     * Remove unnecessary zeros after the decimal point, e.g. "12.000" becomes "12".
     */
    private static String stripZeros(String s) {
        int index = s.lastIndexOf('.');
        if (index == -1) return s;
        for (int i = index + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '0')
                return s;
        }
        return s.substring(0, index);
    }

    private static String nZeros(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++)
            sb.append('0');
        return sb.toString();
    }

}
