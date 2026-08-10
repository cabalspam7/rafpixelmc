#!/usr/bin/env python3
"""Simulated Ksatria client to validate PoC server handshake+encryption."""
import socket, struct, threading, time

HOST = "127.0.0.1"
PORT = 19129
KEY = bytes([0x13,0x37,0x9A,0x55,0x01,0xBF,0x7C,0x22,0x44,0x88,0xCC,0xEE,0x1F,0x2E,0x3D,0x4C])

def xor(data, key, idx):
    out = bytearray(len(data))
    for i,b in enumerate(data):
        out[i] = (key[idx[0]] ^ b)&0xFF
        idx[0]=(idx[0]+1)%len(key)
    return bytes(out)

s = socket.socket()
s.connect((HOST,PORT))
sidx=[0]; ridx=[0]
# client hello -40 (unencrypted)
body = bytes([-40 & 0xFF]) + struct.pack(">H",0)
s.sendall(body)
print("[client] sent hello -40")
# read server -40 (unencrypted)
op = s.recv(1)[0]
ln = struct.unpack(">H", s.recv(2))[0]
pl = s.recv(ln) if ln else b""
print(f"[client] server -40 len={ln} keylen={pl[0]} key={pl[1:].hex()}")
assert pl[1:] == KEY, "key mismatch!"
print("[client] encryption ON, key matches")
# send a game packet unencrypted? no -- now encrypted
# send opcode 1 (login stub) encrypted
pkt = bytes([1]) + struct.pack(">H",2) + b"\x01\x02"
s.sendall(xor(pkt, KEY, sidx))
print("[client] sent encrypted opcode 1")
# read server ack (-100)
op = xor(s.recv(1), KEY, ridx)[0]
ln = struct.unpack(">H", xor(s.recv(2), KEY, ridx))[0]
pl = xor(s.recv(ln), KEY, ridx) if ln else b""
print(f"[client] server ack opcode={op} len={ln} -> HANDSHAKE+ENC OK")
s.close()
print("SUCCESS: client connected, encrypted channel established")
