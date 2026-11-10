package com.datev.dbui.tui;

import static com.datev.dbui.config.ProfileDialogModel.Field.*;
import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.datev.dbui.config.Profile;
import com.datev.dbui.config.ProfileDialogModel;
import com.datev.dbui.config.ProfileDialogModel.Field;
import com.datev.dbui.tui.DialogForm.Button;
import com.datev.dbui.tui.DialogForm.Kind;

class DialogFormTest {
	private static ProfileDialogModel model(String url, String host) {
		return new ProfileDialogModel(new Profile(null, url, host, null, null, null, null, null, null, Map.of()), null);
	}

	@Test
	void focusVisitsAllFieldsInOrderThenTheButtonsThenWraps() {
		var form = new DialogForm(true);
		var seen = new ArrayList<Object>();
		for (int i = 0; i < 14; i++) { seen.add(form.focusedField() != null ? form.focusedField() : form.focusedButton()); form.next(); }
		assertThat(seen).containsExactly(NAME, URL, HOST, PORT, DATABASE, USER, PASSWORD, READ_ONLY, SCHEMA, PROPERTIES,
			Button.SAVE, Button.CONNECT, Button.CANCEL, NAME);
	}

	@Test
	void previousFromTheFirstFieldGoesToTheLastButton() {
		var form = new DialogForm(true);
		form.previous();
		assertThat(form.focusedButton()).isEqualTo(Button.CANCEL);
		assertThat(form.focusedField()).isNull();
	}

	@Test
	void connectButtonExistsOnlyWhenConnectingIsPossible() {
		assertThat(new DialogForm(true).buttons()).containsExactly(Button.SAVE, Button.CONNECT, Button.CANCEL);
		var form = new DialogForm(false);
		assertThat(form.buttons()).containsExactly(Button.SAVE, Button.CANCEL);
		for (int i = 0; i < 10; i++) form.next();
		form.next();
		assertThat(form.focusedButton()).isEqualTo(Button.CANCEL);
	}

	@Test
	void rowsGroupFieldsUnderHeadings() {
		var rows = new DialogForm(true).rows(false);
		var headings = rows.stream().filter(r -> r.kind() == Kind.HEADING).map(DialogForm.Row::text).toList();
		assertThat(headings).containsExactly("Profile", "Connection", "Credentials", "Options");
		var fields = rows.stream().filter(r -> r.kind() == Kind.FIELD).map(DialogForm.Row::field).toList();
		assertThat(fields).containsExactly(Field.values());
		assertThat(rows.stream().filter(r -> r.kind() == Kind.HINT).map(DialogForm.Row::text).toList())
			.contains("Either a JDBC URL, or host, port and database");
		assertThat(rows.stream().filter(r -> r.kind() == Kind.HINT).map(DialogForm.Row::text).toList())
			.anyMatch(text -> text.contains("key=value;key=value"));
		assertThat(rows).hasSize(DialogForm.FULL_ROWS);
	}

	@Test
	void compactRowsDropTheBlankSeparators() {
		var form = new DialogForm(true);
		assertThat(form.rows(true)).noneMatch(r -> r.kind() == Kind.BLANK);
		assertThat(form.rows(true)).hasSize(form.rows(false).size() - (int) form.rows(false).stream().filter(r -> r.kind() == Kind.BLANK).count());
	}

	@Test
	void focusedRowIsTheRowOfTheFocusedFieldAndNoneOnAButton() {
		var form = new DialogForm(true);
		assertThat(form.rows(true).get(form.focusedRow(true)).field()).isEqualTo(NAME);
		form.next(); form.next();
		assertThat(form.rows(false).get(form.focusedRow(false)).field()).isEqualTo(HOST);
		for (int i = 0; i < 8; i++) form.next();
		assertThat(form.focusedRow(true)).isEqualTo(-1);
	}

	@Test
	void theUnusedAlternativeIsDimmedWhileTheOtherHasText() {
		assertThat(DialogForm.dimmed(URL, model("jdbc:postgresql://h/db", null))).isFalse();
		assertThat(DialogForm.dimmed(HOST, model("jdbc:postgresql://h/db", null))).isTrue();
		assertThat(DialogForm.dimmed(PORT, model("jdbc:postgresql://h/db", null))).isTrue();
		assertThat(DialogForm.dimmed(DATABASE, model("jdbc:postgresql://h/db", null))).isTrue();
		assertThat(DialogForm.dimmed(URL, model(null, "localhost"))).isTrue();
		assertThat(DialogForm.dimmed(HOST, model(null, "localhost"))).isFalse();
		assertThat(DialogForm.dimmed(URL, model(null, null))).isFalse();
		assertThat(DialogForm.dimmed(HOST, model(null, null))).isFalse();
		assertThat(DialogForm.dimmed(USER, model("jdbc:postgresql://h/db", null))).isFalse();
	}

	@Test
	void readOnlyCyclesThroughDefaultOnOffInBothDirections() {
		assertThat(DialogForm.cycleReadOnly("", 1)).isEqualTo("true");
		assertThat(DialogForm.cycleReadOnly("true", 1)).isEqualTo("false");
		assertThat(DialogForm.cycleReadOnly("false", 1)).isEmpty();
		assertThat(DialogForm.cycleReadOnly("", -1)).isEqualTo("false");
		assertThat(DialogForm.cycleReadOnly("false", -1)).isEqualTo("true");
		assertThat(DialogForm.cycleReadOnly("true", -1)).isEmpty();
	}

	@Test
	void readOnlyTextNamesWhatDefaultMeans() {
		assertThat(DialogForm.readOnlyText("", true, true)).isEqualTo("(•) default (global: on)   ( ) on   ( ) off");
		assertThat(DialogForm.readOnlyText("true", false, true)).isEqualTo("( ) default (global: off)   (•) on   ( ) off");
		assertThat(DialogForm.readOnlyText("false", false, true)).isEqualTo("( ) default (global: off)   ( ) on   (•) off");
		assertThat(DialogForm.readOnlyText("", false, false)).isEqualTo("‹ default (global: off) ›");
		assertThat(DialogForm.readOnlyText("true", false, false)).isEqualTo("‹ on ›");
		assertThat(DialogForm.readOnlyText("false", true, false)).isEqualTo("‹ off ›");
	}

	@Test
	void scrollOffsetKeepsTheFocusedRowAndOneRowBelowItVisible() {
		assertThat(DialogForm.scrollOffset(16, 13, 0)).isZero();
		assertThat(DialogForm.scrollOffset(16, 13, 11)).isZero();
		assertThat(DialogForm.scrollOffset(16, 13, 12)).isEqualTo(1);
		assertThat(DialogForm.scrollOffset(16, 13, 14)).isEqualTo(3);
		assertThat(DialogForm.scrollOffset(16, 13, 15)).isEqualTo(3);
		assertThat(DialogForm.scrollOffset(16, 13, -1)).isZero();
		assertThat(DialogForm.scrollOffset(10, 13, 9)).isZero();
	}

	@Test
	void keyHintShrinksToFitTheWidthAndKeepsSaveAndCancel() {
		assertThat(DialogForm.hint(true, 90)).isEqualTo("Tab/Shift-Tab move · Enter next · Ctrl-S Save · Ctrl-G Connect · Esc cancel");
		assertThat(DialogForm.hint(false, 90)).isEqualTo("Tab/Shift-Tab move · Enter next · Ctrl-S Save · Esc cancel");
		assertThat(DialogForm.hint(true, 52)).isEqualTo("Tab move · Ctrl-S Save · Ctrl-G Connect · Esc cancel");
		assertThat(DialogForm.hint(true, 46)).isEqualTo("Ctrl-S Save · Ctrl-G Connect · Esc cancel");
		assertThat(DialogForm.hint(true, 40)).isEqualTo("Ctrl-S Save · Esc cancel");
	}
}
