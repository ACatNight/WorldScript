# Contributing to WorldScript

Bug reports, documentation improvements and focused pull requests are welcome.

## Before making a change

- Check existing issues and the [changelog](CHANGELOG.md).
- For substantial features, open an issue describing the use case and expected behavior.
- Keep changes focused. Separate unrelated formatting or refactoring.

## Build and verify

Install JDK 8 and JDK 21. Gradle uses Java 8 for plugin bytecode and Java 21 for the modern Paper API tests.

```sh
./gradlew clean build
```

On Windows, use `./gradlew.bat clean build`. The JAR is written to `build/libs/`.

The build includes JUnit regression tests, the pure-logic test runner and Paper 1.21.8 API compatibility checks. For runtime changes, also test the affected behavior on a real server and state which server build was used.

## Implementation guidelines

- Compile against the supported legacy API; isolate modern API calls behind compatibility adapters.
- Keep Bukkit world and player access on the correct server thread.
- Clean up scheduled tasks and owned runtime resources on disable or reload.
- Put configurable player-facing messages in language files.
- Update English and Chinese documentation when public behavior changes.
- Add focused regression coverage for bug fixes where practical.

## Pull requests

Describe the problem, resulting behavior and validation. Include reproduction steps for fixes, and call out any configuration migration or compatibility limitations.

Do not include server data, credentials, build outputs, third-party plugin binaries or local design experiments. Optional test-server plugins can be kept in the ignored `local/dependencies/` directory.

## Reporting bugs

Use [GitHub Issues](https://github.com/ACatNight/WorldScript/issues). Include the WorldScript version, server build, Java version, relevant configuration, exact reproduction steps and logs with private data removed.
