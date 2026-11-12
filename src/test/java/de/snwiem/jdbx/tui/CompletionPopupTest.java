package de.snwiem.jdbx.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import de.snwiem.jdbx.completion.Candidate;
import de.snwiem.jdbx.completion.Candidate.Kind;
import de.snwiem.jdbx.completion.Completion;

class CompletionPopupTest {

	private static Candidate c(String name) {
		return new Candidate(name, name, Kind.COLUMN);
	}

	private static Completion numbered(int count, int start, int end) {
		return new Completion(start, end, IntStream.range(0, count).mapToObj(i -> c("col" + (10 + i))).toList());
	}

	@Test
	void noCandidatesGiveANoteAndNoPopup() {
		var outcome = CompletionOutcome.of("sel", new Completion(0, 3, java.util.List.of()));
		assertThat(outcome.popup()).isNull();
		assertThat(outcome.edit()).isNull();
		assertThat(outcome.note()).isEqualTo("no completions");
	}

	@Test
	void aSingleCandidateIsInsertedDirectly() {
		var outcome = CompletionOutcome.of("select cu", new Completion(7, 9, java.util.List.of(c("customers"))));
		assertThat(outcome.edit().text()).isEqualTo("select customers");
		assertThat(outcome.edit().cursor()).isEqualTo(16);
		assertThat(outcome.popup()).isNull();
	}

	@Test
	void severalCandidatesInsertTheCommonPrefixAndOpenThePopupForTheRest() {
		var outcome = CompletionOutcome.of("select c", numbered(3, 7, 8));
		assertThat(outcome.edit().text()).isEqualTo("select col1");
		assertThat(outcome.popup().completion().end()).isEqualTo(11);
		assertThat(outcome.popup().selected().insert()).isEqualTo("col10");
		var accepted = outcome.popup().accept(outcome.edit().text());
		assertThat(accepted.text()).isEqualTo("select col10");
	}

	@Test
	void severalCandidatesWithoutCommonPrefixOnlyOpenThePopup() {
		var completion = new Completion(7, 7, java.util.List.of(c("alpha"), c("beta")));
		var outcome = CompletionOutcome.of("select ", completion);
		assertThat(outcome.edit()).isNull();
		assertThat(outcome.popup().completion()).isEqualTo(completion);
	}

	@Test
	void popupShowsAtMostEightCandidatesAndScrollsWithTheSelection() {
		var popup = new CompletionPopup(numbered(12, 0, 0));
		assertThat(popup.visible()).hasSize(8).first().extracting(Candidate::insert).isEqualTo("col10");
		for (int i = 0; i < 8; i++) {
			popup.down();
		}
		assertThat(popup.selected().insert()).isEqualTo("col18");
		assertThat(popup.visible().get(0).insert()).isEqualTo("col11");
		assertThat(popup.selectedRow()).isEqualTo(7);
		popup.up();
		assertThat(popup.selected().insert()).isEqualTo("col17");
		assertThat(popup.selectedRow()).isEqualTo(6);
	}

	@Test
	void navigationWrapsAround() {
		var popup = new CompletionPopup(numbered(12, 0, 0));
		popup.up();
		assertThat(popup.selected().insert()).isEqualTo("col21");
		assertThat(popup.visible().get(7).insert()).isEqualTo("col21");
		popup.down();
		assertThat(popup.selected().insert()).isEqualTo("col10");
		assertThat(popup.selectedRow()).isEqualTo(0);
	}

	@Test
	void acceptReplacesTheRangeWithTheSelectedCandidate() {
		var popup = new CompletionPopup(numbered(3, 7, 9));
		popup.down();
		var edit = popup.accept("select xx from t");
		assertThat(edit.text()).isEqualTo("select col11 from t");
		assertThat(edit.cursor()).isEqualTo(12);
	}
}
