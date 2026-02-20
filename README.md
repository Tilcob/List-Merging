# List Merging

## Add header definitions (`*.json`)

The app loads headers from **two sources**:

1. **Bundled default headers** from `src/main/resources/headers` (included in the build)
2. **External headers** from the `./headers` folder next to the app's start directory

### Option A: Manually in the project (for the next build)

1. Create a new file in `src/main/resources/headers/<Name>.json`.
2. Format:

```json
{
  ‘name’: ‘My header’,
  ‘headers’: [‘Column A’, ‘Column B’]
}
```

3. Run the build (`./gradlew build` or package task).

`index.json` is automatically regenerated during the build.

### Option B: Without rebuild as an ‘extra feature’

1. Create a `headers` folder in the application's start folder.
2. Save `*.json` files with the same format there.
3. Restart the app.

The external files are loaded at startup. If the same `name` already exists in the bundled headers, the external file overwrites the default entry.