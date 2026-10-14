package com.javaaddonengine.scripting;

import com.javaaddonengine.core.AddonCompatibilityException;
import java.util.HashMap;
import java.util.Map;

/**
 * MoLang compatibility engine for common query expressions.
 * Supports arithmetic, comparisons, ternary, and a query/variable context.
 * Unknown queries raise a controlled error instead of crashing.
 */
public final class MolangEngine {

    private MolangEngine() {}

    public static final class Context {
        private final Map<String, Double> queries = new HashMap<>();
        private final Map<String, Double> variables = new HashMap<>();

        public Context query(String name, double value) {
            queries.put(name, value);
            return this;
        }

        public Context variable(String name, double value) {
            variables.put(name, value);
            return this;
        }

        double resolve(String scope, String name) throws AddonCompatibilityException {
            Double v;
            if ("query".equals(scope)) {
                v = queries.get(name);
                if (v == null) throw unsupported("query." + name);
            } else if ("variable".equals(scope) || "var".equals(scope)) {
                v = variables.get(name);
                if (v == null) throw unsupported("variable." + name);
            } else if ("math".equals(scope)) {
                throw unsupported("math." + name + " (math.* not supported, use literals)");
            } else {
                throw unsupported(scope + "." + name);
            }
            return v;
        }

        private static AddonCompatibilityException unsupported(String what) {
            return new AddonCompatibilityException("Unsupported MoLang expression: " + what);
        }
    }

    public static double evaluate(String expression, Context ctx) throws AddonCompatibilityException {
        if (expression == null || expression.trim().isEmpty()) {
            throw new AddonCompatibilityException("Empty MoLang expression");
        }
        Parser p = new Parser(expression, ctx);
        double v = p.parseTernary();
        p.skipWs();
        if (!p.end()) throw new AddonCompatibilityException("Unexpected trailing input in MoLang: '" + p.rest() + "'");
        return v;
    }

    /** True/false evaluation: nonzero = true. */
    public static boolean evaluateBoolean(String expression, Context ctx) throws AddonCompatibilityException {
        return evaluate(expression, ctx) != 0.0;
    }

    private static final class Parser {
        private final String s;
        private final Context ctx;
        private int pos;

        Parser(String s, Context ctx) { this.s = s; this.ctx = ctx; }

        double parseTernary() throws AddonCompatibilityException {
            double cond = parseOr();
            skipWs();
            if (peek() == '?') {
                pos++;
                double a = parseTernary();
                skipWs();
                expect(':');
                double b = parseTernary();
                return cond != 0.0 ? a : b;
            }
            return cond;
        }

        double parseOr() throws AddonCompatibilityException {
            double v = parseAnd();
            while (true) {
                skipWs();
                if (match("||")) v = (v != 0.0 || parseAnd() != 0.0) ? 1.0 : 0.0;
                else return v;
            }
        }

        double parseAnd() throws AddonCompatibilityException {
            double v = parseComparison();
            while (true) {
                skipWs();
                if (match("&&")) v = (v != 0.0 && parseComparison() != 0.0) ? 1.0 : 0.0;
                else return v;
            }
        }

        double parseComparison() throws AddonCompatibilityException {
            double v = parseAdd();
            while (true) {
                skipWs();
                if (match("==")) v = v == parseAdd() ? 1.0 : 0.0;
                else if (match("!=")) v = v != parseAdd() ? 1.0 : 0.0;
                else if (match(">=")) v = v >= parseAdd() ? 1.0 : 0.0;
                else if (match("<=")) v = v <= parseAdd() ? 1.0 : 0.0;
                else if (peek() == '>') { pos++; v = v > parseAdd() ? 1.0 : 0.0; }
                else if (peek() == '<') { pos++; v = v < parseAdd() ? 1.0 : 0.0; }
                else return v;
            }
        }

        double parseAdd() throws AddonCompatibilityException {
            double v = parseMul();
            while (true) {
                skipWs();
                if (peek() == '+') { pos++; v += parseMul(); }
                else if (peek() == '-') { pos++; v -= parseMul(); }
                else return v;
            }
        }

        double parseMul() throws AddonCompatibilityException {
            double v = parseUnary();
            while (true) {
                skipWs();
                if (peek() == '*') { pos++; v *= parseUnary(); }
                else if (peek() == '/') {
                    pos++;
                    double d = parseUnary();
                    v = d == 0.0 ? 0.0 : v / d;
                } else if (peek() == '%') { pos++; v %= parseUnary(); }
                else return v;
            }
        }

        double parseUnary() throws AddonCompatibilityException {
            skipWs();
            if (peek() == '!') {
                pos++;
                if (peek() == '=') throw new AddonCompatibilityException("Unexpected '!=' without left operand in MoLang");
                return parseUnary() == 0.0 ? 1.0 : 0.0;
            }
            if (peek() == '-') { pos++; return -parseUnary(); }
            if (peek() == '(') {
                pos++;
                double v = parseTernary();
                skipWs();
                expect(')');
                return v;
            }
            if (peekIsDigit() || peek() == '.') return parseNumber();
            if (peekIsIdentStart()) return parseScoped();
            throw new AddonCompatibilityException("Unexpected token in MoLang near: '" + rest() + "'");
        }

        double parseScoped() throws AddonCompatibilityException {
            String scope = parseIdent();
            skipWs();
            if (peek() == '.') {
                pos++;
                skipWs();
                String name = parseIdent();
                return ctx.resolve(scope, name);
            }
            // bare identifiers resolve as variable.*
            return ctx.resolve("variable", scope);
        }

        String parseIdent() throws AddonCompatibilityException {
            skipWs();
            int start = pos;
            while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_')) pos++;
            if (start == pos) throw new AddonCompatibilityException("Expected identifier near: '" + rest() + "'");
            return s.substring(start, pos);
        }

        double parseNumber() throws AddonCompatibilityException {
            skipWs();
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            try {
                return Double.parseDouble(s.substring(start, pos));
            } catch (NumberFormatException e) {
                throw new AddonCompatibilityException("Invalid number in MoLang: '" + s.substring(start, pos) + "'");
            }
        }

        void skipWs() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }
        char peek() { return pos < s.length() ? s.charAt(pos) : '\0'; }
        boolean peekIsDigit() { return Character.isDigit(peek()); }
        boolean peekIsIdentStart() { return Character.isLetter(peek()) || peek() == '_'; }
        boolean end() { skipWs(); return pos >= s.length(); }
        String rest() { return pos < s.length() ? s.substring(pos, Math.min(s.length(), pos + 24)) : ""; }

        boolean match(String token) {
            skipWs();
            if (s.startsWith(token, pos)) { pos += token.length(); return true; }
            return false;
        }

        void expect(char c) throws AddonCompatibilityException {
            skipWs();
            if (peek() != c) throw new AddonCompatibilityException("Expected '" + c + "' in MoLang near: '" + rest() + "'");
            pos++;
        }
    }
}
