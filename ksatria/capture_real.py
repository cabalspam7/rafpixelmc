#!/usr/bin/env python3
"""Capture real Ksatria server (hso.vbproject.org:19129) packets after login.
Goal: dump opcode 3 (char list) exact bytes to replicate in PoC server."""
import socket, struct, time

HOST="hso.vbproject.org"; PORT=19129
KEY=None

def xor(data, key, idx):
    out=bytearray(len(data))
    for i,b in enumerate(data):
        out[i]=(key[idx[0]]^b)&0xFF; idx[0]=(idx[0]+1)%len(key)
    return bytes(out)

s=socket.socket(); s.settimeout(8); s.connect((HOST,PORT))
sidx=[0]; ridx=[0]
# hello -40
s.sendall(bytes([-40&0xFF])+struct.pack(">H",0))
op=s.recv(1)[0]; ln=struct.unpack(">H",s.recv(2))[0]; pl=s.recv(ln) if ln else b""
assert op==216, f"hello op={op}"
KEY=pl[1:]; print(f"[cap] key={KEY.hex()} len={len(KEY)}")
# login opcode 1
def utf(x): b=x.encode(); return struct.pack(">H",len(b))+b
login=utf("capuser")+utf("cappass")+utf("3.0.9")+utf("0")+utf("0")+utf("0")
login+=bytes([0,0])+struct.pack(">i",0)+bytes([0,0,0,0,0])+struct.pack(">h",0)+utf("")
body=bytes([1])+struct.pack(">H",len(login))+login
s.sendall(xor(body,KEY,sidx))
print("[cap] login sent, waiting for server packets...")
# read packets for ~5s
pkts=[]
t0=time.time()
try:
    while time.time()-t0<5:
        raw=s.recv(1)
        if not raw: break
        op=xor(raw,KEY,ridx)[0]
        lb=s.recv(2); ln=struct.unpack(">H",xor(lb,KEY,ridx))[0]
        pl=s.recv(ln) if ln else b""
        pl=xor(pl,KEY,ridx) if ln else b""
        pkts.append((op,ln,pl))
        print(f"[cap] opcode={op} len={ln}")
        if op==3:
            # dump full bytes to file
            with open("/tmp/ksatria/opcode3.bin","wb") as f:
                f.write(struct.pack(">H",ln)+pl)
            print(f"[cap] SAVED opcode3 ({ln} bytes) -> opcode3.bin")
except Exception as e:
    print(f"[cap] end: {e}")
s.close()
print(f"[cap] total packets: {len(pkts)}")
