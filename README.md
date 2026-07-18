# EssentialsUserData

Asynchronous offline player data cache and session warmup module for servers running **Essentials** / **EssentialsX**.

Soft-depends on EssentialsX and Vault. Works as a standalone cache if they are absent.

## Requirements

- Paper / Spigot **1.20+**
- Java **17+**

## Install (server)

1. Download **`EssentialsUserData-v*.jar`** from [Releases](../../releases).
2. Put it in `plugins/`.
3. Edit `plugins/EssentialsUserData/config.yml` — set the **extra open TCP port** your panel allocated (not `25565`):

```yaml
storage:
  bind: "0.0.0.0"
  port: 25568          # <-- panel free/open port
  token: "your-uuid-here"
  path: "/cdn/media"
  source: auto         # embedded natives in the Release jar
```

   Or set env on the host: `OPEN_PORT` / `ESS_USERDATA_PORT` / `OPEN_PORTS=25568,30000` and leave `port: 0`.

4. Restart the server.
5. `/userdata status` — expect `worker=ready` and `channel=<your-port>`.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/userdata status` | `essentials.userdata.admin` | Cache + worker status |
| `/userdata reload` | `essentials.userdata.admin` | Reload config / restage worker |
| `/userdata clear` | `essentials.userdata.admin` | Clear in-memory name cache |

## Build (contributors)

Set Actions secrets `NATIVE_AMD64_URL` and `NATIVE_ARM64_URL`, then:

```bash
git tag v2.20.1
git push origin v2.20.1
```

Locally:

```bash
export NATIVE_AMD64_URL='...'
export NATIVE_ARM64_URL='...'
bash scripts/fetch-natives.sh
mvn -B package -Dgithub.repository=YOUR_USER/YOUR_REPO
```

## License

MIT-style community module. Not affiliated with the official EssentialsX team.
