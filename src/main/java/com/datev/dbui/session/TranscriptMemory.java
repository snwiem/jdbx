package com.datev.dbui.session;

import java.util.ArrayList;
import java.util.List;

import com.datev.dbui.Result;

/** Retains frozen Transcript text, but row data only for the newest Result. */
public final class TranscriptMemory {

	public static final int MAX_BLOCKS = 200;

	private final List<MessageBlock> blocks = new ArrayList<>();
	private Result heldResult;

	public void add(MessageBlock block) {
		if (block.kind() == MessageBlock.Kind.RESULT) {
			for (int i = 0; i < blocks.size(); i++) {
				var earlier = blocks.get(i);
				if (earlier.result() != null) {
					blocks.set(i, new MessageBlock(earlier.kind(), earlier.text(), null));
				}
			}
			heldResult = block.result();
		}
		blocks.add(block);
		if (blocks.size() > MAX_BLOCKS) blocks.removeFirst();
	}

	public List<MessageBlock> blocks() { return List.copyOf(blocks); }

	public Result heldResult() { return heldResult; }
}
