#!/usr/bin/env python3
"""Fake vibeproject.my.id/srvip/ returning JSON -> our proxy IP."""
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
class H(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/srvip") or self.path.startswith("/srvip2"):
            data={"servers":[{"name":"VB Project","ip":"104.207.92.238","port":19129,"lang":1}]}
            body=json.dumps(data).encode()
            self.send_response(200)
            self.send_header("Content-Type","application/json")
            self.send_header("Access-Control-Allow-Origin","*")
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404); self.end_headers()
    def log_message(self,*a): pass
HTTPServer(("0.0.0.0",80),H).serve_forever()
