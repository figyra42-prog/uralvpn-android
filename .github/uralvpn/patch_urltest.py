#!/usr/bin/env python3
# UralVPN: one lost packet must not make a server "n/a": a failed ping test is repeated once.
# Each try has its own 6 s limit, so a dead server ends in about 12 s (the core's limit is 15 s) and
# the working ones are not slowed down; concurrency (10) and the total timeout stay as in the core.
# Also runs patch_sessionid.py (session id of the length the server asks for) and patch_xhttpstatus.py (the reason
# of a dead XHTTP connection in the log, no gRPC header on a download GET) and patch_timing.py (the cold open
# of every tested server in the log) on the same core checkout:
# this script is the one the CI step calls, and its hash is in the cache key of the built core.
# Usage (from the core root): patch_urltest.py protocol/group/urltest.go
import os
import subprocess
import sys

path = sys.argv[1]
for extra in ("patch_sessionid.py", "patch_xhttpstatus.py", "patch_timing.py"):
    subprocess.run(
        [sys.executable, "-I", os.path.join(os.path.dirname(os.path.abspath(__file__)), extra), os.getcwd()],
        check=True,
    )
s = open(path).read()

old = "delay, testErr := urlTestLinks(testCtx, links, detour) //H"
new = """var delay uint16 //UralVPN: one retry, own limit per try
\t\t\t\t\tvar testErr error
\t\t\t\t\tfor attempt := 0; attempt < 2; attempt++ {
\t\t\t\t\t\tattemptCtx, attemptCancel := context.WithTimeout(testCtx, 6*time.Second)
\t\t\t\t\t\tdelay, testErr = urlTestLinks(attemptCtx, links, detour)
\t\t\t\t\t\tattemptCancel()
\t\t\t\t\t\tif testErr == nil || testCtx.Err() != nil {
\t\t\t\t\t\t\tbreak
\t\t\t\t\t\t}
\t\t\t\t\t}"""

if new in s:
    print("urltest retry: already patched")
    sys.exit(0)
assert s.count(old) == 1, "urltest retry: anchor not found or not unique"
open(path, "w").write(s.replace(old, new))
print("urltest retry: patched")
