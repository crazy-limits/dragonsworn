package crazylimits.dragonsworn.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Characterization tests: pin what {@link Toml}'s little parser and formatter do now. */
class TomlTest {
	private final List<String> problems = new ArrayList<>();

	private Map<String, Object> parse(String text) {
		return Toml.parse(text, problems);
	}

	/** The one value of a single {@code k = <text>} line, or null when it did not parse. */
	private Object value(String text) {
		return parse("k = " + text).get("k");
	}

	@Test
	void keysTakeTheirTableAsADottedPrefixInFileOrder() {
		Map<String, Object> values = parse("top = 1\n[a]\nx = 2\n[a.b]\ny = 3\n[ c ]\nz = 4\n");
		assertTrue(problems.isEmpty(), problems.toString());
		assertEquals(List.of("top", "a.x", "a.b.y", "c.z"), new ArrayList<>(values.keySet()));
		assertEquals(1L, values.get("top"));
		assertEquals(3L, values.get("a.b.y"));
		assertEquals(4L, values.get("c.z"), "the header's name is stripped");
	}

	@Test
	void anEmptyHeaderGoesBackToTheTopLevel() {
		Map<String, Object> values = parse("[a]\nx = 1\n[]\ny = 2\n");
		assertEquals(1L, values.get("a.x"));
		assertEquals(2L, values.get("y"));
		assertTrue(problems.isEmpty(), problems.toString());
	}

	@Test
	void quotedKeysAndHeadersAreTreatedDifferently() {
		Map<String, Object> values = parse("\"quoted key\" = 1\n[\"t\"]\nk = 2\n");
		assertEquals(1L, values.get("quoted key"), "a key loses its quotes");
		assertEquals(2L, values.get("\"t\".k"), "a header keeps them");
	}

	@Test
	void aRepeatedKeyKeepsTheLastValue() {
		Map<String, Object> values = parse("k = 1\nk = 2\n");
		assertEquals(Map.of("k", 2L), values);
		assertTrue(problems.isEmpty(), "a duplicate is no problem");
	}

	@Test
	void booleansAreLowerCaseOnly() {
		assertEquals(Boolean.TRUE, value("true"));
		assertEquals(Boolean.FALSE, value("false"));
		assertNull(value("True"));
		assertNull(value("yes"));
		assertEquals(2, problems.size());
	}

	@Test
	void wholeNumbersAreLongs() {
		assertEquals(12L, value("12"));
		assertEquals(-3L, value("-3"));
		assertEquals(5L, value("+5"));
		assertEquals(0L, value("-0"));
		assertEquals(1000000L, value("1_000_000"));
		assertEquals(10L, value("1__0"), "underscores are simply dropped, even doubled");
		assertEquals(7L, value("007"), "leading zeros are accepted");
		assertTrue(problems.isEmpty(), problems.toString());
	}

	@Test
	void aWholeNumberTooBigForALongIsAProblem() {
		assertNull(value("99999999999999999999"));
		assertEquals(List.of("line 1: bad value for k: 99999999999999999999"), problems);
	}

	@Test
	void numbersWithAPointOrExponentAreDoubles() {
		assertEquals(0.35, value("0.35"));
		assertEquals(-1.5, value("-1.5"));
		assertEquals(1.0, value("1."), "a trailing point is accepted");
		assertEquals(0.5, value(".5"), "a leading point is accepted");
		assertEquals(100000.0, value("1e5"));
		assertEquals(0.025, value("2.5E-2"));
		assertEquals(1234.5, value("1_234.5"));
		assertTrue(problems.isEmpty(), problems.toString());
	}

	@Test
	void otherNumberFormsAreProblems() {
		for (String text : List.of("0x10", "inf", "nan", "1.2.3", "1e", "--1", "1 2")) assertNull(value(text), text);
		assertEquals(7, problems.size(), problems.toString());
	}

	@Test
	void stringsUnescapeOnlyQuotesAndBackslashes() {
		assertEquals("hello world", value("\"hello world\""));
		assertEquals("", value("\"\""));
		assertEquals("say \"hi\"", value("\"say \\\"hi\\\"\""));
		assertEquals("a\\b", value("\"a\\\\b\""));
		assertEquals("line\\n", value("\"line\\n\""), "\\n stays as written");
		assertEquals("1_000", value("\"1_000\""), "a quoted number stays a string");
		assertEquals("true", value("\"true\""));
		assertTrue(problems.isEmpty(), problems.toString());
	}

	@Test
	void anythingBetweenAnOuterPairOfQuotesIsOneString() {
		assertEquals("a\" \"b", value("\"a\" \"b\""), "no check that the quotes are balanced");
		assertNull(value("\"open"));
		assertNull(value("'single'"), "single quotes are not strings");
	}

	@Test
	void commentsAreStrippedOutsideQuotes() {
		Map<String, Object> values = parse("# a comment\n  # indented\n[t] # header comment\nk = 1 # trailing\ns = \"a # b\" # c\ne = \"q\\\"#\" # d\n");
		assertTrue(problems.isEmpty(), problems.toString());
		assertEquals(1L, values.get("t.k"));
		assertEquals("a # b", values.get("t.s"), "a # inside quotes is kept");
		assertEquals("q\"#", values.get("t.e"), "an escaped quote does not end the string");
		assertEquals(3, values.size());
	}

	@Test
	void aStringEndingInABackslashCannotTakeATrailingComment() {
		// NOTE: looks like a bug: stripComment treats the closing quote of "a\\" as escaped (it only looks at the
		// character before it), so the trailing comment is kept and the value no longer parses. Without the comment it works.
		assertEquals("a\\", value("\"a\\\\\""));
		assertNull(value("\"a\\\\\" # comment"));
		assertEquals(List.of("line 1: bad value for k: \"a\\\\\" # comment"), problems);
	}

	@Test
	void blankLinesAndLineEndingsAreIgnored() {
		Map<String, Object> values = parse("\r\n\n  \t\na = 1\r\nb = 2\r\n");
		assertEquals(Map.of("a", 1L, "b", 2L), values);
		assertTrue(problems.isEmpty(), problems.toString());
		assertTrue(parse("").isEmpty());
	}

	@Test
	void aLoneCarriageReturnDoesNotEndALine() {
		Map<String, Object> values = parse("a = 1\rb = 2");
		assertTrue(values.isEmpty(), values.toString());
		assertEquals(List.of("line 1: bad value for a: 1\rb = 2"), problems);
	}

	@Test
	void whitespaceAroundKeysAndValuesIsStripped() {
		Map<String, Object> values = parse("   spaced   =    3   \n\tk\t=\t\"v\"\t\n");
		assertEquals(3L, values.get("spaced"));
		assertEquals("v", values.get("k"));
	}

	@Test
	void onlyTheFirstEqualsSplitsKeyAndValue() {
		assertNull(parse("\"a=b\" = 1").get("a=b"), "a quoted key cannot hold an =");
		assertEquals(List.of("line 1: bad value for \"a: b\" = 1"), problems);
	}

	@Test
	void malformedLinesAreReportedByLineNumberAndSkipped() {
		Map<String, Object> values = parse("[t]\nok = 1\n[[array]]\n[unclosed\nnokey\n= 5\nempty =\narr = [1, 2]\ninline = { a = 1 }\nlast = 2\n");
		assertEquals(List.of(
				"line 3: bad table header: [[array]]",
				"line 4: bad table header: [unclosed",
				"line 5: expected key = value: nokey",
				"line 6: expected key = value: = 5",
				"line 7: bad value for empty: ",
				"line 8: bad value for arr: [1, 2]",
				"line 9: bad value for inline: { a = 1 }"), problems);
		assertEquals(Map.of("t.ok", 1L, "t.last", 2L), values, "the rest still reads, in the table before the bad headers");
	}

	@Test
	void problemsAreAppendedToTheGivenList() {
		problems.add("earlier");
		parse("bad");
		assertEquals(List.of("earlier", "line 1: expected key = value: bad"), problems);
	}

	@Test
	void formatWritesEachKindAsTheFileHasIt() {
		assertEquals("true", Toml.format(true));
		assertEquals("12", Toml.format(12L));
		assertEquals("12", Toml.format(12));
		assertEquals("0.35", Toml.format(0.35));
		assertEquals("2.0", Toml.format(2.0));
		assertEquals("-1.5", Toml.format(-1.5));
		assertEquals("\"plain\"", Toml.format("plain"));
		assertEquals("\"say \\\"hi\\\" a\\\\b\"", Toml.format("say \"hi\" a\\b"));
		assertEquals("null", Toml.format(null));
	}

	@Test
	void doublesInScientificNotationAreWrittenWithSixDecimals() {
		assertEquals("10000000000.000000", Toml.format(1e10));
		assertEquals("0.000100", Toml.format(1e-4));
		// NOTE: looks like a bug: a double below 5e-7 formats as zero (and a small one loses digits),
		// so it does not read back as written.
		assertEquals("0.000000", Toml.format(1e-7));
		assertEquals(0.0, value(Toml.format(1e-7)));
		assertEquals(0.000123, value(Toml.format(1.23456e-4)));
	}

	@Test
	void formattedValuesReadBackTheSame() {
		List<Object> samples = List.of(true, false, 0L, -42L, Long.MAX_VALUE, Long.MIN_VALUE, 0.35, -1.5, 2.0, 123456.789, 1e10, 0.001,
				"", "plain", "with # hash", "quote \" inside", "back\\slash", "\\\"", "both \\\" and \\\\ mixed", "trailing \\", "= sign", "[not a table]");
		StringBuilder text = new StringBuilder("[t]\n");
		for (int i = 0; i < samples.size(); i++) text.append("v").append(i).append(" = ").append(Toml.format(samples.get(i))).append('\n');
		Map<String, Object> values = parse(text.toString());
		assertTrue(problems.isEmpty(), problems.toString());
		for (int i = 0; i < samples.size(); i++) assertEquals(samples.get(i), values.get("t.v" + i), "sample " + i + ": " + samples.get(i));
	}

	@Test
	void anIntegerSampleReadsBackAsALong() {
		assertEquals(5L, value(Toml.format(5)));
		assertEquals(5.0f, ((Double) value(Toml.format(5.0f))).floatValue(), "a float reads back as a double");
	}
}
