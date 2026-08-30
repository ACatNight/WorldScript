# Editor

WorldScript's editor is designed to be simple: most common settings can be changed through chat buttons or GUI panels.

## Chat Editor

```text
/ws edit <region-id>
```

Common pages:

- `main`: basic region info
- `events`: event list
- `discovery`: discovery prompts
- `conditions`: entry conditions
- `spawn`: spawn rules
- `data`: variables and states

## GUI Editing

GUI panels are used where clicking is easier than typing:

- Toast icons
- Spawn mob selection
- Region lists

## Editing Tips

- Use chat buttons first.
- Use YAML or Kether only for advanced logic.
- Run `/ws validate` after editing.
- Test with `/ws test <region-id>` or `/ws spawn test <rule-id>`.

