# Troubleshooting

## Region Does Not Trigger

1. Run `/ws validate <region-id>`.
2. Check world name and coordinates.
3. Check whether the event is `enabled: true`.
4. Check entry conditions.
5. Check parent-child region coverage.

## Toast Does Not Appear

1. Run `/ws toast diagnose <region-id>`.
2. Check whether Toast is enabled.
3. Check whether the icon exists in your server version.
4. Use `/ws toast test <region-id>`.

## Mobs Do Not Spawn

1. Run `/ws spawn list`.
2. Run `/ws spawn test <rule-id>`.
3. Check whether MythicMobs is installed.
4. Check whether the MythicMobs mob ID exists.
5. Check whether the region has safe spawn points.
6. Check whether max alive has already been reached.

## Placeholder Does Not Render

1. Confirm PlaceholderAPI is installed.
2. Run `/ws reload`.
3. Test with `/papi parse me %worldscript_region_name%`.
4. Confirm the scoreboard, tab, chat, or HUD plugin supports PlaceholderAPI.

