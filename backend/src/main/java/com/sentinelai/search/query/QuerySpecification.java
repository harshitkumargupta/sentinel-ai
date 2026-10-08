package com.sentinelai.search.query;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventOutcome;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.search.query.QueryNode.Comparison;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Turns a parsed query into a JPA {@link Specification}. Values are always bound as parameters
 * (never concatenated), enum values are validated, LIKE wildcards are {@code *} (user {@code %}/{@code _}
 * are escaped), and ordering operators are only allowed on time and numeric fields.
 */
public final class QuerySpecification {

    private static final char ESCAPE = '\\';

    private QuerySpecification() {
    }

    public static Specification<SecurityEvent> of(QueryNode node) {
        if (node == null) {
            return (root, q, cb) -> cb.conjunction();
        }
        validate(node);
        return (root, q, cb) -> predicate(node, root, cb);
    }

    /** Fail fast (400) on bad values before the query runs. */
    private static void validate(QueryNode node) {
        switch (node) {
            case QueryNode.And a -> a.children().forEach(QuerySpecification::validate);
            case QueryNode.Or o -> o.children().forEach(QuerySpecification::validate);
            case QueryNode.Not n -> validate(n.child());
            case Comparison c -> c.values().forEach(v -> convert(c, v));
        }
    }

    private static Predicate predicate(QueryNode node, Root<SecurityEvent> root, CriteriaBuilder cb) {
        return switch (node) {
            case QueryNode.And a -> cb.and(a.children().stream().map(c -> predicate(c, root, cb)).toArray(Predicate[]::new));
            case QueryNode.Or o -> cb.or(o.children().stream().map(c -> predicate(c, root, cb)).toArray(Predicate[]::new));
            case QueryNode.Not n -> cb.not(predicate(n.child(), root, cb));
            case Comparison c -> comparison(c, root, cb);
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate comparison(Comparison c, Root<SecurityEvent> root, CriteriaBuilder cb) {
        Path<?> path = path(root, c.field().path());
        Object first = convert(c, c.values().get(0));
        return switch (c.op()) {
            case EQ -> cb.equal(path, first);
            case NE -> cb.or(cb.notEqual(path, first), cb.isNull(path));
            case GT -> cb.greaterThan((Expression<Comparable>) path, (Comparable) first);
            case GE -> cb.greaterThanOrEqualTo((Expression<Comparable>) path, (Comparable) first);
            case LT -> cb.lessThan((Expression<Comparable>) path, (Comparable) first);
            case LE -> cb.lessThanOrEqualTo((Expression<Comparable>) path, (Comparable) first);
            case LIKE -> cb.like(path.as(String.class), likePattern(c.values().get(0), false), ESCAPE);
            case CONTAINS -> cb.like(path.as(String.class), likePattern(c.values().get(0), true), ESCAPE);
            case IN -> path.in(c.values().stream().map(v -> convert(c, v)).toList());
            case NOT_IN -> cb.or(cb.not(path.in(c.values().stream().map(v -> convert(c, v)).toList())), cb.isNull(path));
        };
    }

    private static Path<?> path(Root<SecurityEvent> root, String dotted) {
        Path<?> p = root;
        for (String part : dotted.split("\\.")) {
            p = p.get(part);
        }
        return p;
    }

    static Object convert(Comparison c, String raw) {
        QueryField f = c.field();
        boolean ordering = switch (c.op()) {
            case GT, GE, LT, LE -> true;
            default -> false;
        };
        if (ordering && f.kind() != QueryField.Kind.TIME && f.kind() != QueryField.Kind.NUMBER) {
            throw new QuerySyntaxException("Operator " + c.op() + " only applies to time and sourceId");
        }
        boolean textOp = c.op() == QueryNode.Op.LIKE || c.op() == QueryNode.Op.CONTAINS;
        if (textOp && f.kind() != QueryField.Kind.TEXT) {
            throw new QuerySyntaxException("LIKE/CONTAINS only apply to text fields, not " + f.displayName());
        }
        try {
            return switch (f.kind()) {
                case TEXT -> raw;
                case NUMBER -> Long.parseLong(raw.trim());
                case TIME -> Instant.parse(raw.trim());
                case EVENT_TYPE -> EventType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                case OUTCOME -> EventOutcome.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                case SEVERITY -> Severity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            };
        } catch (IllegalArgumentException | DateTimeParseException e) {
            List<String> allowed = f.allowedValues();
            String hint = !allowed.isEmpty() ? " (one of " + String.join(", ", allowed) + ")"
                    : f.kind() == QueryField.Kind.TIME ? " (ISO-8601 UTC, e.g. 2026-10-08T00:00:00Z)"
                    : f.kind() == QueryField.Kind.NUMBER ? " (a number)" : "";
            throw new QuerySyntaxException("Invalid value '" + raw + "' for " + f.displayName() + hint);
        }
    }

    /** {@code *} is the wildcard; SQL wildcards typed by the user are matched literally. */
    static String likePattern(String raw, boolean contains) {
        StringBuilder sb = new StringBuilder();
        for (char ch : raw.toCharArray()) {
            switch (ch) {
                case '%', '_', ESCAPE -> sb.append(ESCAPE).append(ch);
                case '*' -> sb.append('%');
                default -> sb.append(ch);
            }
        }
        return contains ? "%" + sb + "%" : sb.toString();
    }
}
