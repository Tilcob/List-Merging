# List-Merging

List-Merging is a JavaFX desktop application for merging multiple input files (Excel/CSV) and exporting a combined result.

## Features

- Merge multiple input files into one output.
- Supports:
    - Excel (`.xls`, `.xlsx`)
    - CSV (`.csv`)
- Background merge/export task with progress indication in the UI.
- Configurable header definitions via JSON.
- Built-in default headers + optional runtime header overrides.

## Tech Stack

- Java 17 (toolchain)
- JavaFX 17
- Gradle
- Jackson (JSON)
- Apache POI (Excel)
- OpenCSV
- SLF4J + Logback
- Construo (native app packaging)

## Project Structure

- `src/main/java/github/tilcob/app/listmerging`
    - `controller/` – JavaFX controller logic
    - `service/` – merge, export, header loading
    - `tasks/` – background task orchestration
    - `model/` – data model classes
- `src/main/resources`
    - `headers/` – bundled header definitions (`*.json` + `index.json`)
    - `github/tilcob/app/listmerging/mainView.fxml` – main UI
    - `icons/` – app icon assets

## Build and Run

### Run locally

```bash
./gradlew run
```

### Run tests

```bash
./gradlew test
```

### Build JAR

```bash
./gradlew shadowJar
```

### Build packaged app (example Windows target)

```bash
./gradlew packageWinX64
```

> Packaging uses Construo and target-specific runtime image steps.

## Using the App

1. Start the application.
2. Click **Merge**.
3. Select multiple Excel/CSV files.
4. Wait for the background process to complete.
5. The app shows the generated export path.

## Header Definitions (`*.json`)

The application loads headers from **two sources**:

1. **Bundled default headers** from `src/main/resources/headers` (included at build time)
2. **External runtime headers** from `./headers` next to the app working directory

### JSON format

```json
{
  "name": "My Header Set",
  "headers": ["Column A", "Column B", "Column C"],
  "headerPosition": "FIRST"
}
```
`headerPosition` ist optional und erlaubt `FIRST` oder `LAST` (Standard: `FIRST`).

## Working with `Test.zip`

The files in `Test.zip` contain two relevant formats:

- **Piping** (`*.xlsx`) with header `Name; Material; DN; Qty.`
- **SolidWorks Assembly** (`*.xls`) with header `POS-NR.; NAME; Weight [g]; Quantity`

Important: In the piping export, the test data contains **`Benennunng`** (typo),
while the standard definition in the project uses `Benennung`. This may cause
recognition via the exact header to fail.

### Recommendation for `Test.zip` (without code change)

Use external runtime headers (folder `./headers`) and overwrite the
piping definition with the notation from the test data:

`headers/Piping.json`

```json
{
  "name": "Piping",
  "headers": ["Naming", "Material", "DN", "Pcs."],
  "headerPosition": "FIRST"
}
```

`headers/SolidWorksAssembly.json`

```json
{
  "name": "SolidWorks Assembly",
  "headers": ["POS NO.", "NAME", "Weight [g]", "Quantity"],
  "headerPosition": "LAST"
}
```

Then restart the app and merge the files from `Test.zip` as usual.

### Note on "adding up" a column (e.g. `Pcs.`/`Quantity`)

Current status: The app counts **identical rows** and writes the frequency
as `Count` in the export. A configurable sum logic via JSON (e.g.
`add name`/`sumColumn`) is not yet implemented.

Translated with DeepL.com (free version)

### Option A: Add headers to the project (requires rebuild)

1. Add a new file to `src/main/resources/headers/<name>.json`.
2. Build the app again.

`index.json` is generated automatically during the build.

### Option B: Add headers at runtime (no rebuild)

1. Create a `headers` folder in the app start/working directory.
2. Add your `*.json` files there.
3. Restart the app.

Behavior:

- Both bundled and external headers are loaded.
- If an external header has the same `name` as a bundled one, the external header overrides it.
- `index.json` is ignored for external runtime header loading.

## Development Notes

- Main module descriptor: `src/main/java/module-info.java`
- Application entry points:
    - `github.tilcob.app.listmerging.Launcher`
    - `github.tilcob.app.listmerging.Application`

## License

No explicit license file is currently included in this repository.