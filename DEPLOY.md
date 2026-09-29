# Running the ATM System live on a free cloud database

## What "live" means for this app

This is a Java Swing desktop application, so it is **not** something a hosting provider can
serve over HTTP - there is no server to keep running. What "live" means here is:

| | |
|---|---|
| **One package** | `target/atm-system.jar` (or `dist-app/ATM/ATM.exe`) contains the app, the MySQL driver, JDateChooser and the images. Nothing else to install or copy. |
| **Live database** | The data lives in a free cloud MySQL that is **never powered off**, so the app works for anyone, from any machine, forever, without a local MySQL install. |

If you want a **URL in a browser** instead of a desktop app, that is a different (web) build -
see "Want it in a browser?" at the bottom.

---

## Provider choice (free, and does not sleep)

| Provider | Free forever | Card needed | Powered off when idle? | Verdict |
|---|---|---|---|---|
| **TiDB Cloud Starter** (ex "Serverless") | yes, 5 GiB rows + 50M RU/month per instance, 5 instances | **no** | **no** - always reachable, scales up/down automatically | **Use this one** |
| Aiven free MySQL | yes, but only 1 GB | no | **yes** - docs say free services can be powered off with no continuous activity | not suitable |
| Oracle Cloud Always Free (MySQL HeatWave) | yes | yes | no, but sign-up is slow and capacity is often unavailable | backup option |
| Railway / Render / Heroku free, PlanetScale | gone or sleep after minutes | - | yes | avoid |

---

## 1. Create the free database (about 5 minutes)

1. Sign in at <https://cloud.tidbcloud.com> (GitHub or Google account, no card).
2. **Create Cluster** -> plan **Starter** -> region closest to you -> **spending limit 0**
   (leave it at 0 to guarantee you are never charged).
3. Wait ~1 minute for the cluster to be ready, then open its **Web UI** -> SQL Editor.
4. Run the schema and the demo data:
   - paste the contents of `db/schema.sql`
   - then paste `db/seed.sql`
   (You can skip this entirely: the app creates the tables itself the first time it connects.)
5. **Connect** -> copy the connection details: **host** (something like
   `gateway-xxxx.prod.aws.tidbcloud.com`), **port 4000**, set your own **password** for the
   `root` user (TiDB Cloud asks you to create one), and note the **user** name it shows you.

## 2. Point the app at it

Copy `atm-db.properties.example` to `atm-db.properties` (same folder) and fill it in:

```properties
db.url=jdbc:mysql://gateway-xxxx.prod.aws.tidbcloud.com:4000/bankmanagementsystem_db?sslMode=REQUIRED&allowPublicKeyRetrieval=true&serverTimezone=UTC
db.user=your_tidb_user
db.pass=your_tidb_password
```

`atm-db.properties` is in `.gitignore`, so the password never gets committed.

Environment variables work too and take priority over the file
(`ATM_DB_URL`, `ATM_DB_USER`, `ATM_DB_PASS`, or `ATM_DB_HOST` / `ATM_DB_PORT` / `ATM_DB_NAME`):

```bat
set ATM_DB_HOST=gateway-xxxx.prod.aws.tidbcloud.com
set ATM_DB_PORT=4000
set ATM_DB_USER=your_tidb_user
set ATM_DB_PASS=your_tidb_password
run-atm.bat
```

## 3. Check the connection before opening the GUI

```bat
java -cp target\atm-system.jar atm.system.Conn
```

```
ATM database check
  url  : jdbc:mysql://gateway-xxxx.prod.aws.tidbcloud.com:4000/bankmanagementsystem_db?...
Database connected successfully! -> jdbc:mysql://gateway-xxxx.prod.aws.tidbcloud.com:4000/bankmanagementsystem_db
  RESULT: OK, login table has 2 row(s)
```

## 4. Run the app

```bat
run-atm.bat
```

Log in with the demo account:

| Card no | PIN |
|---|---|
| `1234567890` | `1234` |
| `9999888877` | `1111` |

---

## Notes that matter for the free tier

- **Never sleeps:** the TiDB instance accepts connections at any time, so the app never has to
  wait for a cold start. No cron pinger is needed.
- **No writes are needed after signup** - deposits, withdrawals, PIN changes and sign-ups all
  write to the same tables the code already used.
- **One connection for the whole app:** the screens each call `new Conn()` on every click and
  never close it. `Conn` now shares a single connection and re-opens it if the cloud side drops
  an idle one, so the app cannot exhaust the provider's connection limit.
- **Schema is self-healing:** on connect, `Conn` creates any missing table, creates the database
  if it is missing, and renames a legacy `bank.type` column to `bank.mode`.
- **Free quota:** 5 GiB of row storage and 50 million request units per month. An ATM demo
  uses a tiny fraction of that. When the quota is used up, reads/writes are throttled until the
  next month - add a spending limit only if you need more.
- **TLS is required** by TiDB Cloud, hence `sslMode=REQUIRED`. Aiven/MySQL hosts work with the
  default `sslMode=PREFERRED`.

## Distributing it

```bat
mvn -q package        :: target/atm-system.jar        - one file, needs Java 17+
.\package-app.ps1     :: dist-app\ATM\ATM.exe         - native app, bundles the JRE
```

Tag a commit `v1.0.0` and the GitHub Actions workflow (`.github/workflows/build-and-release.yml`)
builds the jar, verifies it is self-contained, and attaches it to a GitHub Release.

**Hand out `atm-db.properties.example` (never the real `atm-db.properties`) with the
credentials**, so every copy of the app points at the same live database.

## Want it in a browser?

The desktop app is a Swing app, so to get a real URL you would need a web front end
(the same `atm.system` screens behind HTTP). If you want that, say so and it can be built as a
small Spring Boot app deployed to a free always-on host such as Google Cloud Run
(free tier with min-instances 1) with the same TiDB database behind it.
