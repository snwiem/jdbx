package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import com.datev.dbui.Result;
import com.datev.dbui.config.Settings;
import com.datev.dbui.tui.MessageBlockRenderer;
import org.junit.jupiter.api.Test;

class TranscriptMemoryTest {

	@Test
	void oldestBlocksAreDiscardedButTheHeldResultSurvivesUntilAnotherResultArrives() {
		var transcript = new TranscriptMemory();
		var result = new Result(List.of("id"), List.of(List.of(7)));
		transcript.add(MessageBlock.result(result, Settings.defaults()));
		for (int i = 0; i < 500; i++) transcript.add(MessageBlock.info("message " + i));
		assertThat(transcript.blocks()).hasSize(200);
		assertThat(transcript.blocks().getFirst().text()).isEqualTo("message 300");
		assertThat(transcript.heldResult()).isSameAs(result);
	}

	@Test
	void onlyTheNewestResultKeepsRowsWhileOlderResultsKeepTheirFrozenText() {
		var transcript = new TranscriptMemory();
		for (int i = 0; i < 50; i++) {
			var rows = IntStream.range(0, 1000).mapToObj(n -> List.<Object>of(n)).toList();
			transcript.add(MessageBlock.result(new Result(List.of("id"), rows), Settings.defaults()));
		}
		assertThat(transcript.blocks()).filteredOn(block -> block.result() != null).hasSize(1);
		assertThat(transcript.heldResult().rows()).hasSize(1000);
		assertThat(transcript.blocks().getFirst().text()).startsWith("id\n").contains("999");
		assertThat(new MessageBlockRenderer(false).rows(List.of(transcript.blocks().getFirst()), 80))
			.extracting(MessageBlockRenderer.Row::text).contains("999");
	}
}
