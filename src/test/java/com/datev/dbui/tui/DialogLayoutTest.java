package com.datev.dbui.tui;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import dev.tamboui.layout.Rect;

class DialogLayoutTest {
	@Test
	void formPanelIsFourColumnsNarrowerOnEachSideAndCentered() {
		var layout = DialogLayout.form(120, 40);
		assertThat(layout.tooSmall()).isFalse();
		assertThat(layout.panel().x()).isEqualTo(4);
		assertThat(layout.panel().width()).isEqualTo(112);
		assertThat(layout.panel().y() * 2 + layout.panel().height()).isEqualTo(40);
	}

	@Test
	void formPanelKeepsOneBlankRowAbovAndBelowOnSmallerScreens() {
		var layout = DialogLayout.form(80, 24);
		assertThat(layout.panel()).isEqualTo(new Rect(4, 1, 72, 22));
	}

	@Test
	void formPanelStillWorksAtTheMinimumSize() {
		var layout = DialogLayout.form(60, 20);
		assertThat(layout.tooSmall()).isFalse();
		assertThat(layout.panel()).isEqualTo(new Rect(4, 1, 52, 18));
	}

	@Test
	void formBelowTheMinimumIsTooSmall() {
		assertThat(DialogLayout.form(59, 30).tooSmall()).isTrue();
		assertThat(DialogLayout.form(100, 19).tooSmall()).isTrue();
	}

	@Test
	void formDoesNotGrowTallerThanItsContent() {
		var layout = DialogLayout.form(100, 60);
		assertThat(layout.panel().height()).isEqualTo(DialogLayout.FORM_HEIGHT);
	}

	@Test
	void smallModalHasASensibleMaximumWidthAndItsContentHeight() {
		var layout = DialogLayout.prompt(200, 40, 5);
		assertThat(layout.tooSmall()).isFalse();
		assertThat(layout.panel().width()).isEqualTo(DialogLayout.PROMPT_MAX_WIDTH);
		assertThat(layout.panel().height()).isEqualTo(7);
		assertThat(layout.panel().x()).isEqualTo((200 - DialogLayout.PROMPT_MAX_WIDTH) / 2);
	}

	@Test
	void smallModalUsesTheFourColumnRuleOnNarrowScreensAndHasItsOwnMinimum() {
		assertThat(DialogLayout.prompt(50, 10, 5).panel().width()).isEqualTo(42);
		assertThat(DialogLayout.prompt(39, 10, 5).tooSmall()).isTrue();
		assertThat(DialogLayout.prompt(90, 6, 5).tooSmall()).isTrue();
	}
}
