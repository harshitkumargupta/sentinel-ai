package com.sentinelai.search.query;

import com.sentinelai.search.query.QueryNode.Comparison;
import com.sentinelai.search.query.QueryNode.Op;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recursive-descent parser for the event search language (an AQL-like subset):
 * <pre>
 *   query      := orExpr
 *   orExpr     := andExpr (OR andExpr)*
 *   andExpr    := unary (AND unary)*
 *   unary      := NOT unary | '(' orExpr ')' | comparison
 *   comparison := field op value | field [NOT] IN '(' value (',' value)* ')'
 *   op         := = | != | &gt; | &gt;= | &lt; | &lt;= | LIKE | CONTAINS
 *   value      := 'quoted' | "quoted" | bare-word
 * </pre>
 * Keywords are case-insensitive; {@code ''} escapes a quote. Bounded: query length, comparison
 * count and nesting depth are capped.
 */
public final class QueryParser {

    public static final int MAX_LENGTH = 1000;
    static final int MAX_COMPARISONS = 50;
    static final int MAX_DEPTH = 10;

    private record Token(String text, boolean quoted, int pos) {
    }

    private final List<Token> tokens;
    private int i;
    private int comparisons;

    private QueryParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /** @return the AST, or null for a blank query (match everything) */
    public static QueryNode parse(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        if (query.length() > MAX_LENGTH) {
            throw new QuerySyntaxException("Query is too long (max " + MAX_LENGTH + " characters)");
        }
        QueryParser p = new QueryParser(lex(query));
        QueryNode node = p.orExpr(0);
        if (p.i < p.tokens.size()) {
            Token t = p.tokens.get(p.i);
            throw new QuerySyntaxException("Unexpected '" + t.text() + "' at position " + (t.pos() + 1));
        }
        return node;
    }

    private QueryNode orExpr(int depth) {
        List<QueryNode> parts = new ArrayList<>(List.of(andExpr(depth)));
        while (keyword("OR")) {
            parts.add(andExpr(depth));
        }
        return parts.size() == 1 ? parts.get(0) : new QueryNode.Or(parts);
    }

    private QueryNode andExpr(int depth) {
        List<QueryNode> parts = new ArrayList<>(List.of(unary(depth)));
        while (keyword("AND")) {
            parts.add(unary(depth));
        }
        return parts.size() == 1 ? parts.get(0) : new QueryNode.And(parts);
    }

    private QueryNode unary(int depth) {
        if (depth > MAX_DEPTH) {
            throw new QuerySyntaxException("Query is nested too deeply (max " + MAX_DEPTH + ")");
        }
        if (keyword("NOT")) {
            return new QueryNode.Not(unary(depth + 1));
        }
        if (symbol("(")) {
            QueryNode inner = orExpr(depth + 1);
            expect(")");
            return inner;
        }
        return comparison();
    }

    private QueryNode comparison() {
        Token fieldTok = next("a field name");
        if (fieldTok.quoted()) {
            throw new QuerySyntaxException("Expected a field name at position " + (fieldTok.pos() + 1));
        }
        QueryField field = QueryField.byName(fieldTok.text()).orElseThrow(() -> new QuerySyntaxException(
                "Unknown field '" + fieldTok.text() + "' at position " + (fieldTok.pos() + 1)));
        if (++comparisons > MAX_COMPARISONS) {
            throw new QuerySyntaxException("Too many conditions (max " + MAX_COMPARISONS + ")");
        }
        boolean negated = keyword("NOT");
        if (keyword("IN")) {
            expect("(");
            List<String> values = new ArrayList<>(List.of(value()));
            while (symbol(",")) {
                values.add(value());
            }
            expect(")");
            return new Comparison(field, negated ? Op.NOT_IN : Op.IN, values);
        }
        if (negated) {
            throw new QuerySyntaxException("Expected IN after NOT for field '" + fieldTok.text() + "'");
        }
        Token opTok = next("an operator");
        Op op = switch (opTok.quoted() ? "" : opTok.text().toUpperCase(Locale.ROOT)) {
            case "=", "==" -> Op.EQ;
            case "!=", "<>" -> Op.NE;
            case ">" -> Op.GT;
            case ">=" -> Op.GE;
            case "<" -> Op.LT;
            case "<=" -> Op.LE;
            case "LIKE" -> Op.LIKE;
            case "CONTAINS" -> Op.CONTAINS;
            default -> throw new QuerySyntaxException("Unknown operator '" + opTok.text() + "' at position "
                    + (opTok.pos() + 1) + " (use = != > >= < <= LIKE CONTAINS IN)");
        };
        return new Comparison(field, op, List.of(value()));
    }

    private String value() {
        Token t = next("a value");
        if (!t.quoted() && (t.text().equals("(") || t.text().equals(")") || t.text().equals(","))) {
            throw new QuerySyntaxException("Expected a value at position " + (t.pos() + 1));
        }
        return t.text();
    }

    private boolean keyword(String kw) {
        if (i < tokens.size() && !tokens.get(i).quoted() && tokens.get(i).text().equalsIgnoreCase(kw)) {
            i++;
            return true;
        }
        return false;
    }

    private boolean symbol(String s) {
        if (i < tokens.size() && !tokens.get(i).quoted() && tokens.get(i).text().equals(s)) {
            i++;
            return true;
        }
        return false;
    }

    private void expect(String s) {
        if (!symbol(s)) {
            int pos = i < tokens.size() ? tokens.get(i).pos() + 1 : -1;
            throw new QuerySyntaxException("Expected '" + s + "'" + (pos > 0 ? " at position " + pos : " at end of query"));
        }
    }

    private Token next(String what) {
        if (i >= tokens.size()) {
            throw new QuerySyntaxException("Expected " + what + " at end of query");
        }
        return tokens.get(i++);
    }

    private static List<Token> lex(String s) {
        List<Token> out = new ArrayList<>();
        int n = s.length();
        int p = 0;
        while (p < n) {
            char c = s.charAt(p);
            if (Character.isWhitespace(c)) {
                p++;
            } else if (c == '\'' || c == '"') {
                int start = p++;
                StringBuilder sb = new StringBuilder();
                boolean closed = false;
                while (p < n) {
                    char d = s.charAt(p);
                    if (d == c && p + 1 < n && s.charAt(p + 1) == c) {
                        sb.append(c);
                        p += 2;
                    } else if (d == c) {
                        p++;
                        closed = true;
                        break;
                    } else {
                        sb.append(d);
                        p++;
                    }
                }
                if (!closed) {
                    throw new QuerySyntaxException("Unterminated quote starting at position " + (start + 1));
                }
                out.add(new Token(sb.toString(), true, start));
            } else if (c == '(' || c == ')' || c == ',') {
                out.add(new Token(String.valueOf(c), false, p++));
            } else if (c == '=' || c == '!' || c == '<' || c == '>') {
                int start = p++;
                if (p < n && (s.charAt(p) == '=' || (c == '<' && s.charAt(p) == '>'))) {
                    p++;
                }
                out.add(new Token(s.substring(start, p), false, start));
            } else {
                int start = p;
                while (p < n && !Character.isWhitespace(s.charAt(p)) && "()=!<>,'\"".indexOf(s.charAt(p)) < 0) {
                    p++;
                }
                out.add(new Token(s.substring(start, p), false, start));
            }
        }
        return out;
    }
}
