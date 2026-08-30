# Troubleshooting

If this page does not answer your question, join the [Discord](https://discord.gg/NPSwPHG9R) or email `acatnight@gmail.com`.

## Region does not trigger

Start with:

```text
/ws validate <region-id>
```

Then check:

- world name
- region coordinates
- whether the event is `enabled: true`
- entry conditions
- parent-child coverage

## Toast does not appear

Start with:

```text
/ws toast diagnose <region-id>
```

Then check:

- Toast toggle
- whether the icon item exists on your server version
- whether the player already discovered the region
- whether you should use `/ws toast test <region-id>` for preview

## Mobs do not spawn

Check the rule:

```text
/ws spawn list
```

Then test it directly:

```text
/ws spawn test <rule-id>
```

Common causes:

- MythicMobs is not installed
- the MythicMobs ID is wrong
- the region has no safe spawn point
- max alive is already reached
- no player is nearby

## Placeholder Does Not Render

1. Confirm PlaceholderAPI is installed.
2. Run `/ws reload`.
3. Test with `/papi parse me %worldscript_region_name%`.
4. Confirm the scoreboard, tab, chat, or HUD plugin supports PlaceholderAPI.
