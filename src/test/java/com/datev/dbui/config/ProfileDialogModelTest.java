package com.datev.dbui.config;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProfileDialogModelTest {
	@Test
	void anUnsetReadOnlyValueStaysUnsetSoTheSessionDefaultStillApplies() {
		var given = new Profile("default", null, "localhost", null, "db", "alice", "secret", null, null, Map.of());
		var dialog = new ProfileDialogModel(given, given.password());
		dialog.validate(true);
		assertThat(dialog.savedProfile().readOnly()).isNull();
		assertThat(ConnectionParameters.resolve(new ConnectionOptions(), dialog.connectionProfile(), Settings.defaults().withExecution(1000, null, true)).readOnly()).isTrue();
	}

	@Test
	void startupPrefillsOptionsOverProfileValuesButDoesNotSaveTheOptionPassword() {
		var raw = new Profile("dev", "jdbc:postgresql://saved/db", null, null, null, "saved-user", "${ENV_PASSWORD}", true, "app", Map.of("sslmode", "require"));
		var resolved = new Profile("dev", raw.url(), null, null, null, raw.user(), "env-secret", true, "app", raw.properties());
		var options = new ConnectionOptions();
		options.host = "tunnel";
		options.database = "other";
		options.password = "option-secret";
		var dialog = ProfileDialogModel.prefill(options, resolved, raw, Settings.defaults());
		assertThat(dialog.value(ProfileDialogModel.Field.HOST)).isEqualTo("tunnel");
		assertThat(dialog.value(ProfileDialogModel.Field.URL)).isEmpty();
		assertThat(dialog.connectionProfile().password()).isEqualTo("option-secret");
		assertThat(dialog.savedProfile().password()).isEqualTo("${ENV_PASSWORD}");
		assertThat(dialog.connectionProfile().user()).isEqualTo("saved-user");
		assertThat(dialog.connectionProfile().readOnly()).isTrue();
	}

	@Test
	void validatesFieldsAndMapsAHostProfileWithoutChangingPasswordWhitespace() {
		var dialog = new ProfileDialogModel(new Profile(null, null, null, null, null, null, null, null, null, Map.of()), null);
		assertThatThrownBy(() -> dialog.validate(true)).isInstanceOf(ConfigException.class).hasMessageContaining("Name");
		dialog.set(ProfileDialogModel.Field.NAME, "dev");
		assertThatThrownBy(() -> dialog.validate(true)).hasMessageContaining("Host");
		dialog.set(ProfileDialogModel.Field.HOST, "localhost");
		dialog.set(ProfileDialogModel.Field.DATABASE, "db");
		dialog.set(ProfileDialogModel.Field.USER, "alice");
		dialog.set(ProfileDialogModel.Field.PORT, "70000");
		assertThatThrownBy(() -> dialog.validate(true)).hasMessageContaining("Port");
		dialog.set(ProfileDialogModel.Field.PORT, "5433");
		dialog.set(ProfileDialogModel.Field.READ_ONLY, "maybe");
		assertThatThrownBy(() -> dialog.validate(true)).hasMessageContaining("Read-only");
		dialog.set(ProfileDialogModel.Field.READ_ONLY, "true");
		dialog.set(ProfileDialogModel.Field.PASSWORD, "  exact password  ");
		dialog.set(ProfileDialogModel.Field.SCHEMA, "app");
		dialog.set(ProfileDialogModel.Field.PROPERTIES, "sslmode=require;ApplicationName=custom");
		dialog.validate(true);
		assertThat(dialog.savedProfile()).isEqualTo(new Profile("dev", null, "localhost", 5433, "db", "alice",
			"  exact password  ", true, "app", Map.of("sslmode", "require", "ApplicationName", "custom")));
		dialog.set(ProfileDialogModel.Field.URL, "jdbc:postgresql://localhost/db");
		assertThatThrownBy(() -> dialog.validate(false)).hasMessageContaining("either");
	}

	@Test
	void transientPasswordsAreUsedToConnectButOnlyDialogPasswordsAreSaved() {
		var given = new Profile(null, "jdbc:postgresql://localhost/db?password=url-secret&sslmode=require", null, null,
			null, "alice", "cli-secret", false, null, Map.of("password", "property-secret", "sslmode", "require"));
		var dialog = new ProfileDialogModel(given, null);
		dialog.set(ProfileDialogModel.Field.NAME, "dev");
		assertThat(dialog.value(ProfileDialogModel.Field.PASSWORD)).isEmpty();
		assertThat(dialog.value(ProfileDialogModel.Field.URL)).doesNotContain("url-secret");
		assertThat(dialog.connectionProfile().url()).contains("password=url-secret");
		assertThat(dialog.connectionProfile().password()).isEqualTo("cli-secret");
		assertThat(dialog.savedProfile().password()).isNull();
		assertThat(dialog.savedProfile().url()).isEqualTo("jdbc:postgresql://localhost/db?sslmode=require");
		assertThat(dialog.savedProfile().properties()).containsExactlyEntriesOf(Map.of("sslmode", "require"));
		dialog.set(ProfileDialogModel.Field.PASSWORD, "${MY_PASSWORD}");
		assertThat(dialog.savedProfile().password()).isEqualTo("${MY_PASSWORD}");
	}
}
