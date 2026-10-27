# JER 1.4.0.247 Forge compatibility evidence

The official Modrinth release is `jW52tbTk`, file `JustEnoughResources-1.20.1-1.4.0.247.jar`, SHA-1 `526e788a9b38afc5171fab49b64a5a54e1bb25d7`. The official source branch is `1.20.1` in `way2muchnoise/JustEnoughResources`. Development is pinned to that release, with JEI 15.56.0.205.

The downloaded release's `ForgePlatformHelper.injectApi` bytecode loads `jeresources/api/IJERPlugin` as the annotation type to search, although integrations correctly use `@JERPlugin`. The current official branch instead loads `JERPlugin`. In the packaged client, annotation-only registration produced zero Lumite recipes. `javap -c -p` on the release artifact confirmed the mismatch; the source branch alone was insufficient evidence.

The fallback calls public `JERAPI.getInstance()` from guarded client setup, and then the normal public `IJERAPI.getWorldGenRegistry().register(...)`. A process-wide flag deduplicates fallback and annotation discovery. It does not patch JER, use reflection into private fields, create an alternative graph category, or load JER on a dedicated server. Both variants share one native world-generation recipe. JER's own DIY/profiling mode can replace its entire world-generation registry; this integration respects that user-selected mode.

The distribution is an explicitly labeled candidate-placement estimate from the bundled feature configuration and actual shared `LumiteFeature.positions` algorithm. It is not measured terrain density. JER initializes before a world is available and does not provide remote worldgen registry synchronization through this API; server data-pack overrides may differ. The handbook states this limitation.

The native WorldGenWrapper displays base drop counts, Fortune and Silk Touch, but ignores arbitrary extra Conditional text for ore drops. A public JEI `IRecipeCategoryDecorator` labels this one native Lumite entry **Default estimate** and supplies the configured bounds, placement settings and tool/drop explanation on hover. No other ore entries are changed.

Official references: [release](https://modrinth.com/mod/just-enough-resources-jer/version/jW52tbTk), [Forge discovery](https://github.com/way2muchnoise/JustEnoughResources/blob/1.20.1/Forge/src/main/java/jeresources/forge/ForgePlatformHelper.java), [API registry](https://github.com/way2muchnoise/JustEnoughResources/blob/1.20.1/CommonApi/src/main/java/jeresources/api/IWorldGenRegistry.java).
