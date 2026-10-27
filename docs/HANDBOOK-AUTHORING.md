# Handbook authoring (GuideME 20.1.15)

The release targets Minecraft 1.20.1, Forge 47.4.10 and Java 17. GuideME 20.1.15 is required on both sides; compile against its `api` classifier and run the full artifact. JEI and JER are optional. There is no AE2 dependency and GuideME is not bundled inside Automatons.

`HandbookItem` opens `automatons:handbook` through `GuidesCommon.openGuide` on server item use. `ClientSetup.guides` builds it once during `RegisterClientReloadListenersEvent`, before the initial resource reload takes its guide snapshot. Do not move registration into queued client setup, add a duplicate data-driven guide JSON, or register on every reload/use. All layout classes stay in the client package.

## Edit the installed handbook

The guide is an unpacked resource pack, separate from the mod JAR. In Rackworks Tester, open:

`C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester\resourcepacks\Automatons-Guide\assets\automatons\guides\automatons\handbook\`

Edit any `.md` page directly with a text editor, save as UTF-8, press **F3+T** in Minecraft to reload resources, then reopen the handbook. The start page is `index.md`. Keep the YAML header between the initial `---` lines. No unzip, Python script, mod build or restart is required for text edits. Back up the entire `Automatons-Guide` folder to preserve your pages and illustrations. The pack must remain enabled in **Options → Resource Packs**.

`tools/install_test_instance.ps1` installs and enables this folder alongside the JAR. The installer records hashes of the defaults in `.automatons-guide-defaults.json` in the instance root. Future installs update unchanged defaults and add new files, but preserve your edited files, custom files and deliberate deletions. Existing selections in `options.txt` are retained. Changed defaults and options are backed up under `.automatons-release-backups`; keep the installation record so later updates can distinguish your changes. The installed folder is your editable copy; it is never a Gradle build output.

## Content and links

Repository defaults live in `resourcepacks/Automatons-Guide/assets/automatons/guides/automatons/handbook/`. These Markdown files are the authoritative editable source; no generator rewrites them. A page's API ID is relative to the handbook root, such as `automatons:network/totems.md`. Documentation screenshots live separately under `assets/images/` and are not game textures. `pack.mcmeta` is at the resource pack root and uses Minecraft 1.20.1 resource-pack format 15. The mod JAR retains the handbook item and recipe/rendering code, but contains no guide pages, screenshots or scene NBTs.

Frontmatter `navigation.parent` is guide-root-relative, while Markdown links and image/structure sources are relative to the current page. Every navigation parent must exist and parents must not form a cycle. Use explicit `<a name="supplies" />` for stable fragment links. Associate relevant items using `item_ids`; keep each item associated with one useful destination. Indexed destinations make item-tooltip navigation work.

`<Recipe id="automatons:automaton" />` takes a **recipe ID**. `<RecipeFor id="automatons:automaton" />` takes an **item ID**. The coincident names in this example do not make the two interchangeable. Recipe layouts use the current RecipeManager. `Handbook` adds the custom CreationRecipe through the public `RecipeTypeMappingSupplier` and `LytStandardRecipeBox` APIs, reading the six actual ingredient entries and output. Never substitute ingredient screenshots or separately maintained costs for a live recipe.

Tags use the pinned compiler syntax: quoted IDs/numbers/vectors, literal booleans such as `interactive={true}`, and `tag="{...}"` SNBT for item metadata. This is not HTML, CSS or React. Use symbolic/default text colors for readability. GuideME 20.1.15's actual client fixes its theme to light mode; a dark-theme toggle is not exposed by that release, so testing a second theme is unavailable without changing the dependency.

## Scenes and images

Only when a blueprint changes, run `python tools/author_handbook.py` to convert the five current worksite JSON blueprints into compressed **vanilla structure NBT** in the repository resource pack. This exporter writes only scene assets and their manifest, never pages or screenshots. GuideME cannot import Sponge `.schem` directly. The exporter retains real state properties and dimensions, inserts the matching Totem at the actual anchor, and writes a hash/source manifest. Item frames are omitted from these static structure previews and that omission is explained in the book; build costs still include them. Two additional scenes demonstrate the home/worksite relationship and the actual four-corner Infuser block entity. They render in GuideME's scene level, not a live Network.

Capture GUI illustrations from the packaged game, keep labels legible, and update them whenever controls change. Keep captures under the handbook image directory and reference them with relative Markdown links. Do not regenerate, redraw or resample the user's supplied Lumite PNGs when updating documentation.

## Preview and verification

`gradlew.bat -PguidePreview runClient` supplies the repository resource-pack source path only to that development run, for GuideME live preview. Normal builds contain no machine-specific content path. `gradlew.bat build` produces the mod JAR plus a separate unpacked copy under `build/resourcepacks/Automatons-Guide/`. Distribute both; clients enable the resource pack, while dedicated servers need only the mod and GuideME. Localized pages can later be added beneath `_de_de/` (for example `_de_de/network/totems.md`), with the same relative structure; absent translations fall back to English. No unreviewed translated pages ship.

For optional checks after editing, run `python tools/validate_handbook.py`; use `--pack "<path to Automatons-Guide folder>"` to check an installed copy. Release verification adds `--baseline` to check the original page/recipe/scene counts, and runs `python tools/test_handbook_install.py` for upgrade preservation. Text edits alone require no mod rebuild. With permission to launch the client, `python tools/instance_smoke.py --isolated --current-review --with-jer` (and `--without-jer`) copies the separate pack into a disposable instance and checks compiled pages/recipes/scenes/item associations, GUI scales, search and the rebound shortcut tooltip. GuideME polls physical GLFW key state, so the harness does not claim to simulate holding that key. Inspect its screenshots as well as the assertions. After changing a recipe, reload/reopen and inspect the rendered recipe against the synchronized RecipeManager; after changing a GUI, recapture its image.

## Pinned source references

- [GuideBuilder and registration](https://github.com/AppliedEnergistics/GuideME/blob/v20.1.15/src/main/java/guideme/GuideBuilder.java)
- [Common open API](https://github.com/AppliedEnergistics/GuideME/blob/v20.1.15/src/main/java/guideme/GuidesCommon.java)
- [Frontmatter](https://github.com/AppliedEnergistics/GuideME/blob/v20.1.15/src/main/java/guideme/compiler/Frontmatter.java) and [tag compilers](https://github.com/AppliedEnergistics/GuideME/tree/v20.1.15/src/main/java/guideme/compiler/tags)
- [Recipe extension](https://github.com/AppliedEnergistics/GuideME/blob/v20.1.15/src/main/java/guideme/compiler/tags/RecipeTypeMappingSupplier.java) and [scene API](https://github.com/AppliedEnergistics/GuideME/tree/v20.1.15/src/main/java/guideme/scene)
- [Live preview](https://github.com/AppliedEnergistics/GuideME/blob/v20.1.15/docs/docs/40-live-preview.md) and [translation](https://github.com/AppliedEnergistics/GuideME/blob/v20.1.15/docs/docs/60-translation.md)

Use these 20.1.15 signatures over newer online examples. In particular use ResourceLocation, NBT and Recipe, not Identifier, data components or RecipeHolder.
