#!/usr/bin/env python3
"""
Ksatria Online (Knight Age) private server - PoC / protocol reference.
Reverse-engineered from client jar (com.silverknight.TemMidlet, v3.0.9).

Protocol (from decompiled classes l.java / al.java / h.java):
  CONNECT -> client sends eo(-40) (opcode 0xD8, empty)
  SERVER  -> replies eo(-40) with payload = [keyLen byte][key bytes]
             client then sets encryption flag g=true
  ENCRYPT (g): each byte XOR keystream h[] rolling index (s for send, r for recv)
  PACKET  : [opcode:byte][len:short][payload:bytes]
             when encrypted, len may be 4-byte int for opcodes
             -51,-52,-54,126 else 2-byte short.

This PoC implements the handshake + echo/ack so a real client connects & stays
online. Game logic (chars/maps/combat) is stubbed -- extend eq.* handlers.
"""
import socket, struct, threading, os, sys

HOST = "0.0.0.0"
PORT = 19129
KEY = bytes([0x13,0x37,0x9A,0x55,0x01,0xBF,0x7C,0x22,0x44,0x88,0xCC,0xEE,0x1F,0x2E,0x3D,0x4C])

def xor_stream(data, key, idx):
    out = bytearray(len(data))
    for i,b in enumerate(data):
        out[i] = (key[idx[0]] ^ b) & 0xFF
        idx[0] = (idx[0]+1) % len(key)
    return bytes(out)

def encode_utf(s):
    b = s.encode('utf-8', 'replace')
    return struct.pack(">H", len(b)) + b

class DataStreamReader:
    def __init__(self, data):
        self.buf = data; self.i = 0
    def readByte(self):
        v = self.buf[self.i]; self.i+=1; return v
    def readShort(self):
        v = struct.unpack(">h", self.buf[self.i:self.i+2])[0]; self.i+=2; return v
    def readInt(self):
        v = struct.unpack(">i", self.buf[self.i:self.i+4])[0]; self.i+=4; return v
    def readUTF(self):
        ln = struct.unpack(">H", self.buf[self.i:self.i+2])[0]; self.i+=2
        s = self.buf[self.i:self.i+ln].decode('utf-8','replace'); self.i+=ln; return s

def send_pkt(conn, opcode, payload, key, idx, enc):
    body = bytes([opcode & 0xFF]) + struct.pack(">H", len(payload)) + payload
    if enc:
        body = xor_stream(body, key, idx)
    conn.sendall(body)

def recv_pkt(conn, key, idx, enc):
    # read opcode
    op = conn.recv(1)
    if not op: return None
    op = op[0]
    if enc:
        op = xor_stream(bytes([op]), key, idx)[0]
    # length: when encrypted, opcodes -51,-52,-54,126 => 4-byte len
    if enc and op in (205,204,202,126):
        ln = struct.unpack(">I", conn.recv(4))[0]
        if enc: ln = xor_stream(struct.pack(">I",ln), key, idx)  # already xored? handled below
    else:
        ln = struct.unpack(">H", conn.recv(2))[0]
    # NOTE: in encrypted mode the length bytes are also XORed on the wire.
    # Simpler correct path: read raw then de-xor whole payload after reading len raw.
    return op, ln

def handle(conn, addr):
    print(f"[+] connect {addr}")
    enc = False
    sidx = [0]; ridx = [0]
    try:
        # read client's first packet (opcode -40 = 0xD8), unencrypted
        op = conn.recv(1)
        if not op: return
        op = op[0]
        ln = struct.unpack(">H", conn.recv(2))[0]
        _payload = conn.recv(ln) if ln else b""
        print(f"    client hello opcode={op} len={ln}")
        # reply -40 with key
        payload = bytes([len(KEY)]) + KEY
        send_pkt(conn, -40, payload, KEY, sidx, False)
        enc = True
        print(f"    sent handshake key, encryption ON")
        # main loop: ack packets so client stays alive
        while True:
            raw = conn.recv(1)
            if not raw: break
            op = raw[0]
            op = xor_stream(bytes([op]), KEY, ridx)[0]
            # read length (encrypted: 2-byte short normally)
            lb = conn.recv(2)
            ln = struct.unpack(">H", xor_stream(lb, KEY, ridx))[0]
            pl = conn.recv(ln) if ln else b""
            pl = xor_stream(pl, KEY, ridx) if ln else b""
            print(f"    <- opcode={op} len={ln}")
            if op == 1:
                try:
                    ds = DataStreamReader(pl)
                    user = ds.readUTF(); pw = ds.readUTF(); ver = ds.readUTF()
                    s4 = ds.readUTF(); s5 = ds.readUTF(); s6 = ds.readUTF()
                    bx_b = ds.readByte(); z0 = ds.readByte(); ni = ds.readInt()
                    by2 = ds.readByte(); fi_h = ds.readByte(); ftG = ds.readByte()
                    bsk = ds.readByte(); ftB = ds.readByte(); ftI = ds.readShort()
                    ftF = ds.readUTF()
                    print(f"    LOGIN user={user} ver={ver} int={ni}")
                    wpayload = bytes([0]) + encode_utf("KsatriaPoC") + struct.pack(">h",-1) + bytes([7])
                    send_pkt(conn, -100, wpayload, KEY, sidx, True)
                    print(f"    sent -100 welcome")
                except Exception as e:
                    print(f"    login parse err: {e}")
                continue
            # ACK other packets: echo -100 welcome stub to keep alive
            send_pkt(conn, -100, b"\x00", KEY, sidx, True)
    except Exception as e:
        print(f"[-] {addr} closed: {e}")
    finally:
        conn.close()

def main():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind((HOST, PORT))
    s.listen(5)
    print(f"[*] Ksatria PoC server on {HOST}:{PORT}")
    while True:
        c,a = s.accept()
        threading.Thread(target=handle, args=(c,a), daemon=True).start()

if __name__ == "__main__":
    main()
