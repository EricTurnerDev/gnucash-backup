# gnucash_backup.bb

A [Babashka](https://github.com/babashka/babashka)-powered tool for dumping a GnuCash PostgreSQL
database.

---

## ✨ Features

- **Logging**
    - Console + logfile with timestamps. 
- **Configurable**
    - Reasonable defaults can be overridden with command-line options.
- **Pruning**
    - Only retain a certain number of backup (.dump) files.
- **Safety**
    - Preflight checks for:
      - Script already running.
      - Missing dependencies.

---

## 🚀 Usage

```bash
gnucash_backup.bb --output DIR [options]
```

### Options

| Flag                 | Description                                               |
|----------------------|-----------------------------------------------------------|
| `-d`, `--database`   | The PostgreSQL GnuCash database. Defaults to `gnucash_db` |
| `-h`, `--help`       | Display helpful information.                              |
| `-H`, `--host`       | The PostgreSQL host. Defaults to 127.0.0.1.               |
| `-k`, `--keep`       | The number of backups to keep. Defaults to 5.             |
| `-o`, `--output-dir` | The directory to save the backup file in. REQUIRED.       |
| `-p`, `--port`       | The PostgreSQL port. Defaults to 5432.                    |
| `-u`, `--user`       | The PostgreSQL database user. Defaults to gnucash_user.   |
| `-v`, `--version`    | Display the version.                                      |

Examples:

```bash
gnucash_backup.bb --output /tmp --keep 7
gnucash_backup.bb --help
gnucash_backup.bb --version
```

---

## 📦 Installation

### Requirements
- Linux (tested on Linux Mint)
- [Babashka](https://github.com/babashka/babashka) (v1.3+ recommended)
- PostgreSQL GnuCash database running
- `postgresql-client-common` package (for `pg_dump`)
- `~/.pgpass` file containing a line like `127.0.0.1:5432:*:gnucash_user:my-secret-password`

### System-wide (optional)

Put `gnucash_backup.bb` somewhere in root's `$PATH`, e.g.:

```bash
sudo cp gnucash_backup.bb /usr/local/sbin/
sudo chmod +x /usr/local/sbin/gnucash_backup.bb
```

---

## 📜 Exit Codes

| Code | Meaning                   |
|------|---------------------------|
| `0`  | Success                   |
| `1`  | Failed                    |

---

## 🔧 Development

### Project Layout
- `src/gnucash_backup.bb` — main Babashka script

---

## 🤝 Contributing

Ideas for future improvements:
- Better log management (e.g. rotate logs)
- Customizable log path
- Notifications (email, Slack, Telegram, healthchecks.io, etc)
- Indicate if a newer version of the script is available

Fork, hack, and send a PR 🚀

---

## 📄 License

[MIT](LICENSE)

---

## 🖼️ Example Log Output

```
2025-10-02 10:46:00 [INFO] Running gnucash_backup.bb ...
2025-10-02 10:46:00 [INFO] Version 0.0.1
2025-10-02 10:46:00 [INFO] Logging to /tmp/gnucash_backup.bb.log
2025-10-02 10:46:00 [INFO] Lock file /tmp/gnucash_backup.bb.lock
2025-10-02 10:46:00 [INFO] Database URL postgresql://gnucash_user:********@127.0.0.1:5432/gnucash_db
2025-10-02 10:46:00 [INFO] Backing up database to /tmp/gnucash_20251002-104600.dump
2025-10-02 10:46:00 [INFO] Deleting old backups (keeping 5).
2025-10-02 10:46:00 [INFO] Done
```