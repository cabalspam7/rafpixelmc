# Ksatria Online — Private Server Research & Proxy

Reverse-engineered from the official **Ksatria Online (Knight Age) v3.0.9** client
(`com.silverknight.TemMidlet`). This repo contains the protocol research, a working
handshake/login PoC server, and a transparent proxy to the live game server.

> Status: protocol reversed (handshake + encryption + login). Game logic (char/world/
> combat) is not reimplemented — instead a **proxy** is provided so the real client
> works 100% through our host. Use it to learn the protocol and build a true private
> server.

## Protocol (from decompiled client)

Packet wire format:
```
[opcode: byte] [length: short] [payload: bytes]
```
After the handshake the channel is **XOR-encrypted** with a rolling keystream `h[]`.

### Handshake
1. Client connects, sends `eo(-40)` (opcode `0xD8`, empty payload, **unencrypted**).
2. Server replies `eo(-40)` with payload `[keyLen: byte][key: bytes]`.
3. Client sets encryption flag `g=true` and uses `h = key` for all further traffic.

### Login (opcode `1`, client -> server)
Sent via `q.a(user, pass, version, "0","0","0", -1, -1)`:
```
writeUTF(user)
writeUTF(pass)
writeUTF(version)      // "3.0.9"
writeUTF("0") writeUTF("0") writeUTF("0")
writeByte(bx.b) writeByte(0)
writeInt(n)            // -1 = register, 0 = login
writeByte(-1)
writeByte(fi.h?1:0) writeByte(ft.G) writeByte(bs.k)
writeByte(ft.B?1:0) writeShort(ft.I) writeUTF(ft.F)
```

### Welcome (opcode `-100` / `0x9C`, server -> client)
```
readByte()        // status
readUTF()         // server name
readShort()       // -1 = no world jump
readByte()        // must == 7
```

## Files

| File | Purpose |
|------|---------|
| `server_poc.py` | PoC server: handshake + encryption + login parsing + welcome. Listens `0.0.0.0:19129`. |
| `proxy.py` | Transparent proxy `0.0.0.0:19129` -> `hso.vbproject.org:19129`. Logs every packet to `/root/proxy_pkts.log`. |
| `srvip_server.py` | Fake `vibeproject.my.id/srvip/` HTTP on `:80` returning JSON pointing to this host. |
| `client_sim.py` / `client_sim_login.py` | Python simulators validating handshake + login round-trip. |
| `capture_real.py` | Capture raw packets from the live server after login. |
| `decompiled/` | Decompiled client classes (`l.java`=net layer, `al.java`=reader/dispatch, `eq.java`=server->client handlers, `q.java`=login sender). |

## Deploy (VPS 104.207.92.238)

```bash
# 1. game proxy (relays to real server)
nohup python3 proxy.py > proxy.log 2>&1 &

# 2. fake srvip so DNS-spoofed clients point here
nohup python3 srvip_server.py > srvip.log 2>&1 &
```

Ports: `19129/tcp` (game), `80/tcp` (srvip json).

## How a client reaches our host

The official client fetches its server IP from `https://vibeproject.my.id/srvip/`.
Point DNS `vibeproject.my.id` -> `104.207.92.238` (hosts file / DNS spoof) and the
client will fetch our fake `/srvip/` (returns this host) then connect to our proxy on
`:19129`, which relays to the real game server. Full gameplay works through the proxy.

## Next steps to a true private server

1. Capture opcode `3` (char list, handler `eq.b`) and `44`/`49` (world/map) from a
   live proxied session — exact bytes are logged in `proxy_pkts.log`.
2. Replicate those byte layouts in `server_poc.py` (replace the proxy upstream with
   locally generated packets).
3. Implement remaining `eq.*` handlers (7000 lines) for full offline play.

## Disclaimer

For research / educational use. Ksatria Online is a third-party game; respect its terms.
