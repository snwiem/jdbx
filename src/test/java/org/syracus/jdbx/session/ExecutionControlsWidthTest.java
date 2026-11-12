package org.syracus.jdbx.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.syracus.jdbx.config.Settings;

class ExecutionControlsWidthTest {

	private final AtomicReference<Settings> current = new AtomicReference<>(Settings.defaults());
	private final ExecutionControls controls = new ExecutionControls(current::get, current::set);

	@Test
	void aNumberSetsTheMaximumCellWidth() {
		var block = controls.width("40");
		assertThat(current.get().maxWidth()).isEqualTo(40);
		assertThat(block.kind()).isEqualTo(MessageBlock.Kind.INFO);
		assertThat(block.text()).isEqualTo("Maximum cell width: 40");
	}

	@Test
	void zeroAndOffBothMeanNoCut() {
		for (String argument : new String[] { "0", "off" }) {
			current.set(Settings.defaults());
			var block = controls.width(argument);
			assertThat(current.get().maxWidth()).isZero();
			assertThat(block.text()).isEqualTo("Maximum cell width: off");
		}
	}

	@Test
	void otherSettingsStayUnchanged() {
		var before = Settings.defaults();
		controls.width("12");
		var after = current.get();
		assertThat(after.rowLimit()).isEqualTo(before.rowLimit());
		assertThat(after.format()).isEqualTo(before.format());
		assertThat(after.orientation()).isEqualTo(before.orientation());
		assertThat(after.nullMarker()).isEqualTo(before.nullMarker());
	}

	@Test
	void theConverterAcceptsNumbersFromZeroAndOffAndRejectsEverythingElse() {
		assertThat(ExecutionControls.widthArgument("0")).isEqualTo("0");
		assertThat(ExecutionControls.widthArgument("2147483647")).isEqualTo("2147483647");
		assertThat(ExecutionControls.widthArgument("off")).isEqualTo("off");
		for (String bad : new String[] { "-1", "abc", "1.5", "2147483648", "OFF ", "" }) {
			assertThatThrownBy(() -> ExecutionControls.widthArgument(bad))
				.isInstanceOf(picocli.CommandLine.TypeConversionException.class)
				.hasMessage("Use an integer from 0 to 2147483647, or off");
		}
	}
}
