# Configuration

WorldScript keeps configuration split across focused files instead of putting everything into one large `config.yml`.

Common directory layout:

```text
plugins/WorldScript/
├─ config.yml
├─ settings/
├─ lang/
├─ regions/
└─ modules/
```

## Region Files

Region files are stored in:

```text
plugins/WorldScript/regions/
```

Use `schema: 2` for new regions.

## Language Files

Language files are stored in:

```text
plugins/WorldScript/lang/
```

Bundled languages:

- `en_US.yml`
- `zh_CN.yml`
- `zh_TW.yml`

After changing language, run:

```text
/ws reload
```

