#!/usr/bin/env python3
# UralVPN: the ping test must survive a poor connection (up to 3 tries per server, each with its own
# 8 s limit and a fresh connection, fewer parallel tests, longer total time), like Happ does.
# Usage (from the core root): patch_urltest.py protocol/group/urltest.go
import sys

path = sys.argv[1]
s = open(path).read()

pairs = [
    (
        "batch.WithConcurrencyNum[any](10)",
        "batch.WithConcurrencyNum[any](4)",
    ),
    (
        "context.WithTimeout(b.ctx, C.TCPTimeout)",
        "context.WithTimeout(b.ctx, 25*time.Second) //UralVPN: room for retries",
    ),
    (
        "delay, testErr := urlTestLinks(testCtx, links, detour) //H",
        """var delay uint16 //UralVPN: retries, one lost packet must not make a server red
\t\t\t\t\tvar testErr error
\t\t\t\t\tfor attempt := 0; attempt < 3; attempt++ {
\t\t\t\t\t\tattemptCtx, attemptCancel := context.WithTimeout(testCtx, 8*time.Second) //UralVPN: own limit per attempt, a hung one is cut and retried on a fresh connection
\t\t\t\t\t\tdelay, testErr = urlTestLinks(attemptCtx, links, detour)
\t\t\t\t\t\tattemptCancel()
\t\t\t\t\t\tif testErr == nil || testCtx.Err() != nil {
\t\t\t\t\t\t\tbreak
\t\t\t\t\t\t}
\t\t\t\t\t\ttime.Sleep(200 * time.Millisecond)
\t\t\t\t\t}""",
    ),
]

if all(new in s for _, new in pairs):
    print("urltest retry: already patched")
    sys.exit(0)
for old, new in pairs:
    assert s.count(old) == 1, "urltest retry: anchor not found or not unique: " + old
    s = s.replace(old, new)
open(path, "w").write(s)
print("urltest retry: patched")
