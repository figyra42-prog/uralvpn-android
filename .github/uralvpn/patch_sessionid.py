#!/usr/bin/env python3
# UralVPN: the length and the alphabet of the XHTTP session id (Xray's sessionIDLength / sessionIDTable).
# The panel's CDN-Timeweb hosts give "sessionIDLength": "4-8", "sessionIDTable": "Base62" and the server
# refuses a session id that is not of this form; the core always sent a 36-character UUID.
# - option/v2ray_transport.go: two new options, session_id_length (range) and session_id_table (string);
# - transport/v2rayxhttp/ural_sessionid.go (new file): the generator;
# - transport/v2rayxhttp/client.go: the client takes the id from the generator (the UUID stays the default,
#   when the length is not set).
# Run from the core root: patch_sessionid.py <core-root>   (called by patch_urltest.py, i.e. by the CI step)
import os
import re
import sys

root = sys.argv[1]

# 1) options
p = os.path.join(root, "option", "v2ray_transport.go")
s = open(p).read()
if "SessionIDLength" in s:
    print("session id options: already patched")
else:
    pat = re.compile(r'(\tSessionKey\s+string\s+`json:"session_key,omitempty"`\n)')
    assert len(pat.findall(s)) == 1, "SessionKey field not found"
    s = pat.sub(
        lambda m: m.group(1)
        + '\tSessionIDLength      Xbadoption.Range       `json:"session_id_length"`\n'
        + '\tSessionIDTable       string                 `json:"session_id_table,omitempty"`\n',
        s,
    )
    open(p, "w").write(s)
    print("session id options: patched")

# 2) the generator (a new file of the same package)
gen = os.path.join(root, "transport", "v2rayxhttp", "ural_sessionid.go")
open(gen, "w").write('''package xhttp

import (
	"crypto/rand"
	"math/big"
	"strings"

	"github.com/sagernet/sing-box/option"
)

// uralSessionID returns a session id of the length (and the alphabet) the server asks for; with no length
// set it returns fallback (the UUID of the original code).
func uralSessionID(options *option.V2RayXHTTPOptions, fallback string) string {
	length := options.SessionIDLength
	if length.From <= 0 && length.To <= 0 {
		return fallback
	}
	n := int(length.Rand())
	if n < 1 {
		n = int(length.From)
	}
	if n < 1 {
		return fallback
	}
	alphabet := uralSessionAlphabet(options.SessionIDTable)
	limit := big.NewInt(int64(len(alphabet)))
	result := make([]byte, n)
	for i := range result {
		v, err := rand.Int(rand.Reader, limit)
		if err != nil {
			return fallback
		}
		result[i] = alphabet[v.Int64()]
	}
	return string(result)
}

func uralSessionAlphabet(table string) string {
	const digits = "0123456789"
	const lower = "abcdefghijklmnopqrstuvwxyz"
	const upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
	switch strings.ToLower(strings.TrimSpace(table)) {
	case "", "base62":
		return digits + upper + lower
	case "base36":
		return digits + lower
	case "base32":
		return "abcdefghijklmnopqrstuvwxyz234567"
	case "hex", "base16":
		return "0123456789abcdef"
	case "base10", "digits":
		return digits
	}
	if len(table) >= 2 { // a literal alphabet
		return table
	}
	return digits + upper + lower
}
''')
print("session id generator: written")

# 3) the client
client = os.path.join(root, "transport", "v2rayxhttp", "client.go")
c = open(client).read()
if "uralSessionID(" in c:
    print("session id in the client: already patched")
else:
    old = "\t\tsessionIdUuid := uuid.New()\n\t\tsessionId = sessionIdUuid.String()\n"
    assert c.count(old) == 1, "session id lines not found (%d)" % c.count(old)
    c = c.replace(
        old,
        "\t\tsessionIdUuid := uuid.New()\n\t\tsessionId = uralSessionID(c.options, sessionIdUuid.String()) //UralVPN: length and alphabet as the server asks\n",
    )
    open(client, "w").write(c)
    print("session id in the client: patched")
