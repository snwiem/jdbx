package com.datev.dbui.config;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProfileDialogModelTest {
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
		assertThat(dialog.connectionProfile().password()).isEqualTo("cli-secret");
		assertThat(dialog.savedProfile().password()).isNull();
		assertThat(dialog.savedProfile().url()).isEqualTo("jdbc:postgresql://localhost/db?sslmode=require");
		assertThat(dialog.savedProfile().properties()).containsExactlyEntriesOf(Map.of("sslmode", "require"));
		dialog.set(ProfileDialogModel.Field.PASSWORD, "${MY_PASSWORD}");
		assertThat(dialog.savedProfile().password()).isEqualTo("${MY_PASSWORD}");
	}
}
