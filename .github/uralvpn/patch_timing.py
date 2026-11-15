#!/usr/bin/env python3
# UralVPN: a measurement of the COLD open of every server, to find out which kind of server is slow to open.
# The ping shown in the app is the faster of two requests over one connection (a warm one). Here the core also
# keeps how long the connection took to be made ("connect": TCP + TLS/QUIC to the server) and how long the first
# request took after that (the proxy handshake + TLS to the target + the request), and writes ONE log entry
# with all the servers tested (only for the explicit tests: the ping button), sorted from the fastest cold open.
# - common/urltest/ural_timing.go (new file): the storage and the report;
# - common/urltest/urltest.go: the two times are recorded;
# - protocol/group/urltest.go: the report goes into the log after a forced test.
# Run from the core root: patch_timing.py <core-root>   (called by patch_urltest.py)
import os
import sys

root = sys.argv[1]


def swap(text, old, new, what):
    assert text.count(old) == 1, "%s not found (%d)" % (what, text.count(old))
    return text.replace(old, new)


# 1) the new file
timing = os.path.join(root, "common", "urltest", "ural_timing.go")
open(timing, "w").write('''package urltest

import (
	"fmt"
	"sort"
	"strings"
	"sync"
)

// UralVPN: cold open timing of the servers (see patch_timing.py).

type uralTimingKey struct{}

type uralTiming struct {
	connectMs int64
	firstMs   int64
}

// UralTiming is the last measurement of one server.
type UralTiming struct {
	ConnectMs int64
	FirstMs   int64
	ShownMs   int64
	Type      string
}

var uralTimings sync.Map // tag -> UralTiming

// UralTimingReport lists the cold open of the servers that were just tested, the fastest first.
func UralTimingReport(tested map[string]uint16) string {
	type row struct {
		tag    string
		timing UralTiming
	}
	rows := make([]row, 0, len(tested))
	for tag := range tested {
		if value, ok := uralTimings.Load(tag); ok {
			rows = append(rows, row{tag, value.(UralTiming)})
		}
	}
	sort.Slice(rows, func(i, j int) bool {
		return rows[i].timing.ConnectMs+rows[i].timing.FirstMs < rows[j].timing.ConnectMs+rows[j].timing.FirstMs
	})
	var builder strings.Builder
	for _, item := range rows {
		fmt.Fprintf(&builder, "%4d ms cold (connect %d + first request %d), ping shown %d ms: %s [%s]\\n",
			item.timing.ConnectMs+item.timing.FirstMs, item.timing.ConnectMs, item.timing.FirstMs,
			item.timing.ShownMs, item.tag, item.timing.Type)
	}
	return builder.String()
}
''')
print("timing: ural_timing.go written")

# 2) record the times
path = os.path.join(root, "common", "urltest", "urltest.go")
s = open(path).read()
if "uralTimingKey" in s:
    print("timing: urltest.go already patched")
else:
    s = swap(
        s,
        "\tstart := time.Now()\n\tinstance, err := detour.DialContext(",
        "\tstart := time.Now()\n\turalBegin := start //UralVPN: cold timing\n\tinstance, err := detour.DialContext(",
        "dial start",
    )
    s = swap(
        s,
        "\tdefer instance.Close()\n\tif N.NeedHandshakeForWrite(instance) {\n",
        "\tdefer instance.Close()\n\turalConnect := time.Since(uralBegin) //UralVPN\n\tif N.NeedHandshakeForWrite(instance) {\n",
        "dial end",
    )
    s = swap(
        s,
        "\tt = uint16(time.Since(start) / time.Millisecond)\n",
        "\tt = uint16(time.Since(start) / time.Millisecond)\n"
        "\tif timing, ok := ctx.Value(uralTimingKey{}).(*uralTiming); ok { //UralVPN\n"
        "\t\ttiming.connectMs = uralConnect.Milliseconds()\n"
        "\t\ttiming.firstMs = int64(t)\n"
        "\t}\n",
        "first request",
    )
    s = swap(
        s,
        "\treturn urlTest(ctx, link, detour)\n}\n\nfunc urlTest(",
        "\ttiming := &uralTiming{} //UralVPN: the cold timing goes into the report\n"
        "\tctx = context.WithValue(ctx, uralTimingKey{}, timing)\n"
        "\tt, err := urlTest(ctx, link, detour)\n"
        "\tif err == nil {\n"
        "\t\tif out, ok := detour.(adapter.Outbound); ok {\n"
        "\t\t\turalTimings.Store(out.Tag(), UralTiming{ConnectMs: timing.connectMs, FirstMs: timing.firstMs, ShownMs: int64(t), Type: out.Type()})\n"
        "\t\t}\n"
        "\t}\n"
        "\treturn t, err\n}\n\nfunc urlTest(",
        "wrapper",
    )
    open(path, "w").write(s)
    print("timing: urltest.go patched")

# 3) the report in the log
group = os.path.join(root, "protocol", "group", "urltest.go")
g = open(group).read()
if "UralTimingReport" in g:
    print("timing: group already patched")
else:
    anchor = "\ttestBatch.test(outbounds, links, interval, force) //H\n\tb.Wait()\n"
    addition = (
        "\tif force { //UralVPN: one log entry with the cold open of every tested server\n"
        "\t\tif report := urltest.UralTimingReport(testBatch.result); report != \"\" {\n"
        "\t\t\tlogger.Info(\"ural-timing, cold open of the servers:\\n\", report)\n"
        "\t\t}\n"
        "\t}\n"
    )
    g = swap(g, anchor, anchor + addition, "report")
    open(group, "w").write(g)
    print("timing: group patched")
