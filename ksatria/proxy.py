#!/usr/bin/env python3
"""
Ksatria Online - PROXY mode (learning + relay).
Client <-> this proxy <-> real server hso.vbproject.org:19129
Logs every packet (opcode, len) so we can reverse exact formats.
Later: replace upstream responses with our own -> true private server.
"""
import socket, struct, threading, sys

LISTEN_HOST="0.0.0.0"; LISTEN_PORT=19129
UP_HOST="hso.vbproject.org"; UP_PORT=19129
KEY_DEFAULT=bytes([0x13,0x37,0x9A,0x55,0x01,0xBF,0x7C,0x22,0x44,0x88,0xCC,0xEE,0x1F,0x2E,0x3D,0x4C])

def xor(data,key,idx):
    out=bytearray(len(data))
    for i,b in enumerate(data):
        out[i]=(key[idx[0]]^b)&0xFF; idx[0]=(idx[0]+1)%len(key)
    return bytes(out)

def logpkt(tag,op,ln):
    with open("/root/proxy_pkts.log","a") as f:
        f.write(f"{tag} op={op} len={ln}\n")

def pipe(src,dst,tag,key_holder,idx,enc_ref):
    """relay from src->dst, optionally xor if key known. enc_ref=[bool]."""
    try:
        while True:
            op=src.recv(1)
            if not op: break
            opb=op[0]
            lb=src.recv(2)
            if len(lb)<2: break
            ln=struct.unpack(">H",lb)[0]
            pl=src.recv(ln) if ln else b""
            if not pl and ln>0: break
            # detect key on client hello (-40 unencrypted) to learn upstream key
            if tag=="C->S" and opb==216 and ln>0:
                key_holder[0]=pl[1:len(pl)]
            # relay (we don't re-xor; just pass raw bytes through)
            dst.sendall(op+lb+pl)
            logpkt(tag,opb,ln)
    except Exception as e:
        pass
    finally:
        try: dst.close()
        except: pass

def handle(client,addr):
    print(f"[+] client {addr}")
    try:
        up=socket.socket(); up.settimeout(10); up.connect((UP_HOST,UP_PORT))
    except Exception as e:
        print(f"[-] upstream fail: {e}"); client.close(); return
    key=[b'']
    t1=threading.Thread(target=pipe,args=(client,up,"C->S",key,[0],None),daemon=True)
    t2=threading.Thread(target=pipe,args=(up,client,"S->C",key,[0],None),daemon=True)
    t1.start(); t2.start()
    t1.join(); t2.join()
    print(f"[-] client {addr} done")

def main():
    s=socket.socket(); s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
    s.bind((LISTEN_HOST,LISTEN_PORT)); s.listen(5)
    print(f"[*] Ksatria PROXY on {LISTEN_HOST}:{LISTEN_PORT} -> {UP_HOST}:{UP_PORT}")
    while True:
        c,a=s.accept()
        threading.Thread(target=handle,args=(c,a),daemon=True).start()

if __name__=="__main__":
    main()
