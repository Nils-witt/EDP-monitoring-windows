# edpmonitoring — Windows client

## Overview

This repository contains the Windows client for "edpmonitoring" — a small Java-based agent that syncs unit status between a MariaDB database and a remote web API. The project builds an executable Windows bundle (exe) that includes a bundled JRE so it can be run on Windows machines without a separately installed JRE.

## Highlights
- Built with Maven and Java 21
- Produces an uber/shaded jar and a Windows executable (.exe) using Launch4j
- Optionally bundles a JRE (directory `jre/`) for the packaged exe
- Uses MariaDB JDBC driver and OkHttp for API communication

## Prerequisites
- JDK 21 for building (openjdk-21 or equivalent)
- Apache Maven 3.6+ (mvn)
- On macOS/Linux you can still build the project; the produced exe targets Windows (Launch4j). The shaded jar can be executed on any JVM 21 runtime.

## Quickstart

1) Copy the example configuration

Place a copy of `config.properties.example` next to the executable or in the working directory where you run the app, renaming it to `config.properties` and edit the values.

2) Run the packaged jar (quick, cross-platform)

- Build (see below) then run:

    java -jar target/windows-client-1.0-SNAPSHOT-shaded.jar

Make sure `config.properties` is in the current working directory when you run the jar.

3) Run the packaged Windows executable (on Windows)

- After a successful `mvn package` the installer/executable appears under `target/` with a name like `windows-client-1.0-SNAPSHOT.exe` and a bundled `jre/` is copied into `target/jre/` by the build. Copy the `config.properties` next to the exe and run it on a Windows machine.

## Build

- To build everything (shaded jar + exe):

    mvn clean package

- If you only want the shaded jar (no exe):

    mvn clean package -DskipTests

### Notes on the build process
- The Maven configuration will invoke `scripts/fetch-jre.sh` during generate-resources to ensure `jre/` exists. The script is idempotent and will no-op if the `jre/` folder is already present.
- The Launch4j plugin wraps the shaded jar into a Windows exe and references the `jre/` folder that gets copied into `target/jre/`.

## Configuration

See `config.properties.example` for full, commented keys. Key configuration options:

- db.url — JDBC URL for MariaDB (example: jdbc:mariadb://127.0.0.1:3306/edp_monitoring)
- db.user — database username
- db.password — database password

- api.url — Base URL of the remote API (e.g. https://api.example.com)
- api.token — Optional: API token (JWT). If not provided, the client will try to log in using username/password.
- api.username — Optional API username (used when api.token is empty)
- api.password — Optional API password

- unit.[n].name — Mapping entries for units (human-readable name used in DB)
- unit.[n].api_id — Corresponding unit id on the remote API

- db.units.upload — if true, the client will upload units found in the DB to the remote API on startup (use with care)

## Database

- A small sample SQL snippet is included at `data/sample.sql` showing expected schema / example rows. Adapt it to your environment.

## Logging

- The application uses Log4j2. By default logs are written to `logs/` (see the project's log4j2 configuration in the source tree). Check `logs/app.log` and rotated files for runtime output.

## Running & Troubleshooting

- If the client reports authentication failures against the API, verify `api.token` or `api.username`/`api.password` in `config.properties` and ensure the `api.url` is correct.
- You can enable verbose logs by adjusting Log4j2 configuration (source log config under `src/main/resources` if present).
- Database connection issues: confirm `db.url`, `db.user`, `db.password` and that the MariaDB server accepts connections from the host running the client.
- If building fails because the `jre/` folder is missing, run `scripts/fetch-jre.sh` manually or ensure it is present. The script is idempotent.

## Development

- IDE: Import as a Maven project. The main entrypoint is `dev.nilswitt.rk.edpmonitoring.Main` (configured as the shade plugin's main class).
- Java version: 21 (see `pom.xml` maven.compiler.source/target)

## Packaging / Installer

- An NSIS installer definition is provided at `installer.nsi` (Windows installer automation). The project includes a minimal Launch4j configuration via the Maven plugin which produces an exe in `target/`.
