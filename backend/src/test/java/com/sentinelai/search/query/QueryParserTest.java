package com.sentinelai.search.query;

import com.sentinelai.search.CsvWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Grammar, precedence, quoting, value validation, limits and error messages. */
class QueryParserTest {

    @Test
    void andBindsTighterThanOr() {
        QueryNode n = QueryParser.parse("user = 'a' OR user = 'b' AND outcome = 'FAILURE'");
        assertThat(n).isInstanceOf(QueryNode.Or.class);
        QueryNode.Or or = (QueryNode.Or) n;
        assertThat(or.children().get(1)).isInstanceOf(QueryNode.And.class);
    }

    @Test
    void parenthesesNotInAndQuotes() {
        QueryNode n = QueryParser.parse("(ip = \"10.0.0.5\" or ip = 10.0.0.6) and not eventType in ('OTHER', 'API_ABUSE') and resource = 'it''s'");
        assertThat(n).isInstanceOf(QueryNode.And.class);
        QueryNode.And and = (QueryNode.And) n;
        assertThat(and.children().get(0)).isInstanceOf(QueryNode.Or.class);
        assertThat(and.children().get(1)).isInstanceOf(QueryNode.Not.class);
        QueryNode.Comparison quoted = (QueryNode.Comparison) and.children().get(2);
        assertThat(quoted.values()).containsExactly("it's");

        QueryNode.Comparison notIn = (QueryNode.Comparison) QueryParser.parse("type NOT IN (OTHER)");
        assertThat(notIn.op()).isEqualTo(QueryNode.Op.NOT_IN);
        assertThat(QueryParser.parse("   ")).isNull();
    }

    @Test
    void helpfulErrors() {
        assertThatThrownBy(() -> QueryParser.parse("color = 'red'")).hasMessageContaining("Unknown field 'color'");
        assertThatThrownBy(() -> QueryParser.parse("ip ~ 'x'")).hasMessageContaining("Unknown operator");
        assertThatThrownBy(() -> QueryParser.parse("ip = 'x")).hasMessageContaining("Unterminated quote");
        assertThatThrownBy(() -> QueryParser.parse("ip = 'x' AND")).hasMessageContaining("at end of query");
        assertThatThrownBy(() -> QueryParser.parse("(ip = 'x'")).hasMessageContaining("Expected ')'");
        assertThatThrownBy(() -> QueryParser.parse("ip = 'x' user = 'y'")).hasMessageContaining("Unexpected 'user'");
        assertThatThrownBy(() -> QueryParser.parse("x".repeat(1001))).hasMessageContaining("too long");
        assertThatThrownBy(() -> QueryParser.parse("(".repeat(12) + "ip = 1" + ")".repeat(12)))
                .hasMessageContaining("nested too deeply");
    }

    @Test
    void valueValidationAndLikeEscaping() {
        assertThatThrownBy(() -> QuerySpecification.of(QueryParser.parse("outcome = 'MAYBE'")))
                .hasMessageContaining("one of SUCCESS, FAILURE, UNKNOWN");
        assertThatThrownBy(() -> QuerySpecification.of(QueryParser.parse("time > 'yesterday'")))
                .hasMessageContaining("ISO-8601");
        assertThatThrownBy(() -> QuerySpecification.of(QueryParser.parse("user > 'a'")))
                .hasMessageContaining("only applies to time");
        assertThatThrownBy(() -> QuerySpecification.of(QueryParser.parse("type LIKE 'FAIL*'")))
                .hasMessageContaining("text fields");
        assertThat(QuerySpecification.likePattern("adm*n_%", false)).isEqualTo("adm%n\\_\\%");
        assertThat(QuerySpecification.likePattern("x", true)).isEqualTo("%x%");
    }

    @Test
    void csvCellsAreQuotedAndFormulaSafe() {
        assertThat(CsvWriter.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(CsvWriter.cell("+1")).isEqualTo("'+1");
        assertThat(CsvWriter.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvWriter.cell(null)).isEmpty();
    }
}
