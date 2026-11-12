package org.syracus.jdbx.tui;

import java.util.List;

import org.syracus.jdbx.session.MessageBlock;

/** Retains Message blocks, their wrapped rows, and the distance from the newest row. */
final class Transcript {

	private final MessageBlockRenderer renderer;
	private List<MessageBlock> blocks = List.of();
	private List<MessageBlockRenderer.Row> rows = List.of();
	private int width = -1;
	private int aboveBottom;
	private int viewport;

	Transcript(MessageBlockRenderer renderer) {
		this.renderer = renderer;
	}

	void replace(List<MessageBlock> blocks) {
		this.blocks = List.copyOf(blocks);
		width = -1;
		aboveBottom = 0;
	}

	List<MessageBlockRenderer.Row> rows(int width, int viewport) {
		if (this.width != width) {
			rows = renderer.rows(blocks, width);
			this.width = width;
		}
		this.viewport = viewport;
		scroll(0);
		return rows;
	}

	void scroll(int distance) {
		aboveBottom = Math.clamp(aboveBottom + distance, 0, Math.max(0, rows.size() - viewport));
	}

	void page(int direction) {
		scroll(direction * Math.max(1, viewport - 1));
	}

	int first() {
		return Math.max(0, rows.size() - viewport - aboveBottom);
	}
}
