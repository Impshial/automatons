# Current run — 0.7.0

The approved request is archived in reference/current-run/specification.txt. Implementation is the default unless the user explicitly asks otherwise.

1. Preserve the supplied ore PNG bytes and make resource generation respect them. Add fuel pieces, remove every mod command and disable the old retrogen scheduler while retaining history.
2. Update the shared felling rules and validate three trunk levels, entity occupancy, protections and exact payouts. Increase the new-chunk vein size from the verified baseline 6 to 8.
3. Verify and integrate optional JER 1.4.0.247 and required GuideME 20.1.15 against their actual 1.20.1 APIs.
4. Write the handbook against the final gameplay, add real recipes/scenes/screenshots and check navigation, assets and runtime behavior.
5. Run server/world/client checks, then package, install and archive the release using the existing workflow.

Collector checkpoint: passive collection from surrounding planted flowers/plants; placing a bottle allows it to fill; drinking transfers Lumina to the player. Range, rate, eligibility, plant consumption, internal capacity, bottle capacity, recipe, partial fills and extraction remain undecided. No collector currently exists; none will be implemented in this run.


The historical Collector checkpoint above was superseded by the approved 0.8.0 brief. See [Luminall implementation](LUMINALL.md) for the implemented Collector, Tank and fluid.
