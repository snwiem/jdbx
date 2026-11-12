package org.syracus.jdbx.config;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileStoreTest {
	@TempDir Path directory;

	private Profile profile(String name, String host) {
		return new Profile(name, null, host, 5432, "db", "alice", "${DB_PASSWORD}", false, "public", Map.of("sslmode", "require"));
	}

	@Test
	void atomicReplacementNeverExposesPartialYamlAndLeavesNoTemporaryFiles() throws Exception {
		var writer = new ProfileStore(directory);
		var reader = new ProfileStore(directory);
		writer.save(profile("dev", "before"));
		var started = new java.util.concurrent.CountDownLatch(1);
		var finished = new java.util.concurrent.atomic.AtomicBoolean();
		try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			var reads = workers.submit(() -> {
				int count = 0;
				do {
					assertThat(reader.profile("dev").database()).isEqualTo("db");
					count++; started.countDown();
				} while (!finished.get());
				return count;
			});
			started.await();
			try { for (int i = 0; i < 30; i++) writer.save(profile("dev", "after" + i + "x".repeat(2000))); }
			finally { finished.set(true); }
			assertThat(reads.get()).isPositive();
		}
		try (var files = Files.list(directory)) {
			assertThat(files.map(p -> p.getFileName().toString())).containsExactlyInAnyOrder("profiles.yaml", ".profiles.lock");
		}
	}

	@Test
	void warnsForWidePermissionsAndRemovesOnlyTheChosenProfile() throws Exception {
		var store = new ProfileStore(directory);
		store.save(profile("keep", "one"));
		store.save(profile("remove", "two"));
		Files.setPosixFilePermissions(directory.resolve("profiles.yaml"), java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
		assertThat(store.warning()).contains("profiles.yaml", "600");
		store.remove("remove");
		assertThat(store.names()).containsExactly("keep");
		assertThat(store.warning()).isNull();
		assertThatThrownBy(() -> store.remove("missing")).isInstanceOf(ConfigException.class).hasMessageContaining("Unknown Profile");
		assertThatThrownBy(() -> store.profile("missing")).isInstanceOf(ConfigException.class);
	}

	@Test
	void concurrentStoresMergeOnlyTheChangedProfileIntoFreshContent() throws Exception {
		Files.writeString(directory.resolve("profiles.yaml"), "profiles:\n  untouched:\n    host: elsewhere\n    future-key: retained\nroot-extension: retained\n");
		var one = new ProfileStore(directory);
		var two = new ProfileStore(directory);
		var start = new java.util.concurrent.CountDownLatch(1);
		try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			var a = workers.submit(() -> { start.await(); for (int i = 0; i < 15; i++) one.save(profile("a" + i, "one")); return null; });
			var b = workers.submit(() -> { start.await(); for (int i = 0; i < 15; i++) two.save(profile("b" + i, "two")); return null; });
			start.countDown();
			a.get(); b.get();
		}
		for (int i = 0; i < 15; i++) {
			assertThat(one.profile("a" + i).host()).isEqualTo("one");
			assertThat(two.profile("b" + i).host()).isEqualTo("two");
		}
		assertThat(Files.readString(directory.resolve("profiles.yaml"))).contains("future-key: retained", "root-extension: retained");
	}

	@Test
	void savesAProfileWithALiteralPasswordReferenceAndPrivateMode() throws Exception {
		var store = new ProfileStore(directory);
		store.save(profile("dev", "localhost"));
		assertThat(store.profile("dev")).isEqualTo(profile("dev", "localhost"));
		assertThat(Files.readString(directory.resolve("profiles.yaml"))).contains("profiles:", "${DB_PASSWORD}");
		assertThat(Files.getPosixFilePermissions(directory.resolve("profiles.yaml")))
			.isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
		try (var files = Files.list(directory)) {
			assertThat(files.map(p -> p.getFileName().toString())).doesNotContainAnyElementsOf(java.util.List.of("profiles.yaml.tmp"));
		}
	}
}
