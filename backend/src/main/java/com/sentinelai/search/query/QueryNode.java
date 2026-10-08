package com.sentinelai.search.query;

import java.util.List;

/** Parsed event-search query (AST). */
public sealed interface QueryNode {

    record And(List<QueryNode> children) implements QueryNode {
    }

    record Or(List<QueryNode> children) implements QueryNode {
    }

    record Not(QueryNode child) implements QueryNode {
    }

    /** {@code field op value(s)}; IN / NOT_IN carry several values, everything else one. */
    record Comparison(QueryField field, Op op, List<String> values) implements QueryNode {
    }

    enum Op { EQ, NE, GT, GE, LT, LE, LIKE, CONTAINS, IN, NOT_IN }
}
