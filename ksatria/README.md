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

---

## Opcode `3` (char list / world join) — reversed layout (`eq.b`)

Handler `eq.b` reads (server -> client), in order:

```
short   s2            // char/account id
UTF     name          // lowercased
int     n2,n3,n4,n5   // stats (hp-related)
byte    s3,by2,by3,s4 // class/level/etc
byte    s3b           // equipment count
cz[]    s3b items: (byte a, int b)        // a=slot, b=value  (ver 3.0.9 uses readInt)
short   cn.f.by, cn.f.bz
short   bq.t, bq.u
short[] bq.v[0][bq.f], bq.v[1][bq.f]        // bq.f = array length
byte[]  bq.I[bq.f], bq.J[bq.f]
byte    cv, short cA, byte by4 (=bq.x)
short   s6; if s6>=0: int, int, UTF, byte  // some ref
UTF     cn.S; long cn.T
byte    by5; short[] sArray[by5]
byte    ft.K
try short cn.f.aM
try byte  cn.f.dU
try short cn.f.aN..aV  (several, each try/catch -> -1 on fail)
```

> Implementer note: `bq.f` (array lengths) is set elsewhere; without a live capture the
> exact counts are unknown, so a blind reimplementation is fragile. The **proxy** is the
> reliable path: any opcode-3 sent by the real server through `proxy.py` is auto-saved to
> `/root/opcode3_capture.bin` for byte-exact replication.

## Capture status

- Proxy auto-saves opcode `3` raw bytes to `/root/opcode3_capture.bin` when seen.
- Direct capture from `hso.vbproject.org:19129` failed: the live server drops the
  connection immediately after the handshake (no `-100` welcome, no login response)
  for unauthenticated/anonymous connections. A valid in-game account + real client is
  required to obtain a live opcode-3 sample.
