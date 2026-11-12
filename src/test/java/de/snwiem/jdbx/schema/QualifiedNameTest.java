package de.snwiem.jdbx.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QualifiedNameTest {

	@Test
	void unquotedNameIsFoldedToLowerCaseLikePostgreSql() {
		var name = QualifiedName.parse("Orders");
		assertThat(name.schema()).isNull();
		assertThat(name.name()).isEqualTo("orders");
	}

	@Test
	void quotedNameKeepsCaseAndSpaces() {
		assertThat(QualifiedName.parse("\"My Table\"").name()).isEqualTo("My Table");
	}

	@Test
	void schemaQualifiedNameSplitsAtTheDot() {
		var name = QualifiedName.parse("Sales.\"Line Items\"");
		assertThat(name.schema()).isEqualTo("sales");
		assertThat(name.name()).isEqualTo("Line Items");
	}

	@Test
	void dotInsideQuotesDoesNotSplitAndDoubledQuoteIsOneQuote() {
		var name = QualifiedName.parse("\"a.b\".\"c\"\"d\"");
		assertThat(name.schema()).isEqualTo("a.b");
		assertThat(name.name()).isEqualTo("c\"d");
	}

	@Test
	void malformedNamesAreRejected() {
		assertThatThrownBy(() -> QualifiedName.parse("\"open")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> QualifiedName.parse("a.b.c")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> QualifiedName.parse("")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> QualifiedName.parse("a.")).isInstanceOf(IllegalArgumentException.class);
	}
}
