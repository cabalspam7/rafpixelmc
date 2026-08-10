#!/usr/bin/env python3
"""Simulated Ksatria client: hello -> handshake -> login(opcode1) -> welcome(-100)."""
import socket, struct

HOST = "127.0.0.1"
PORT = 19129
KEY = bytes([0x13,0x37,0x9A,0x55,0x01,0xBF,0x7C,0x22,0x44,0x88,0xCC,0xEE,0x1F,0x2E,0x3D,0x4C])

def xor(data, key, idx):
    out = bytearray(len(data))
    for i,b in enumerate(data):
        out[i] = (key[idx[0]] ^ b)&0xFF
        idx[0]=(idx[0]+1)%len(key)
    return bytes(out)

def enc_pkt(op, payload, sidx):
    body = bytes([op&0xFF]) + struct.pack(">H", len(payload)) + payload
    return xor(body, KEY, sidx)

def dec_pkt(conn, ridx):
    op = xor(conn.recv(1), KEY, ridx)[0]
    ln = struct.unpack(">H", xor(conn.recv(2), KEY, ridx))[0]
    pl = xor(conn.recv(ln), KEY, ridx) if ln else b""
    return op, ln, pl

def utf(s):
    b = s.encode(); return struct.pack(">H", len(b)) + b

s = socket.socket(); s.connect((HOST,PORT))
sidx=[0]; ridx=[0]
# hello -40
s.sendall(bytes([-40&0xFF]) + struct.pack(">H",0))
# read -40 raw (unencrypted)
op = s.recv(1)[0]
ln = struct.unpack(">H", s.recv(2))[0]
pl = s.recv(ln) if ln else b""
assert op == 216 and pl[1:] == KEY, f"handshake fail op={op}"
print("[ok] handshake, key matches")
# login opcode 1
login = utf("testuser")+utf("testpass")+utf("3.0.9")+utf("0")+utf("0")+utf("0")
login += bytes([0,0]) + struct.pack(">i",0) + bytes([0,0,0,0,0]) + struct.pack(">h",0) + utf("")
s.sendall(enc_pkt(1, login, sidx))
print("[ok] sent login opcode 1")
op,ln,pl = dec_pkt(s, ridx)
print(f"[ok] server -100 welcome: status={pl[0]} len={ln}")
assert op == 156, f"expected -100 got {op}"
print("SUCCESS: login flow works (handshake + encrypted login + welcome)")
s.close()
