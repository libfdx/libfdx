# Scoreboard

Keep score for two teams with UI Kit. The match is saved after each point and restored when you reopen the app.

Use **+1 point** on either team's card to score. **New match** resets both scores. The shared `ScoreboardApplication` uses two `UiIntState` values for the display and flushes them together to a local `KeyValueStore`. Desktop stores the match on disk. Web stores it in the browser for the same site.

## Run from the libFDX checkout

```shell
./gradlew :samples:ui:scoreboard:platform:desktop:libfdx_desktop_jvm_gl_run
./gradlew :samples:ui:scoreboard:platform:web:libfdx_web_js_webgl_run
```

In an exported project, the corresponding task paths start with `:platform:desktop:` or `:platform:web:`.
