#!/usr/bin/env python3
# UralVPN: the XHTTP client must say why a connection died. In the hiddify port a download request that the server (or
# the CDN) answers with a status other than 200 is closed silently, and a failed upload request only interrupts the
# pipe: the log shows nothing. Now the reader returns the reason ("xhttp: server answered 403 Forbidden") and a failed
# upload closes the download with its error, so the core log shows it.
# Run from the core root: patch_xhttpstatus.py <core-root>   (called by patch_urltest.py)
import os
import sys

root = sys.argv[1]
dialer = os.path.join(root, "transport", "v2rayxhttp", "dialer.go")
client = os.path.join(root, "transport", "v2rayxhttp", "client.go")


def swap(text, old, new, what):
    assert text.count(old) == 1, "%s not found (%d)" % (what, text.count(old))
    return text.replace(old, new)


d = open(dialer).read()
if "func (w *WaitReadCloser) abort(" in d:
    print("xhttp status: dialer already patched")
else:
    d = swap(
        d,
        "\t\t\tresp.Body.Close() // if it is called immediately, the upload will be interrupted also\n\t\t\treader.Close()    //H\n",
        "\t\t\tresp.Body.Close() // if it is called immediately, the upload will be interrupted also\n"
        "\t\t\tif !uploadOnly {\n\t\t\t\treader.abort(E.New(\"xhttp: server answered \", resp.Status)) //UralVPN: the reason is visible in the log\n\t\t\t}\n"
        "\t\t\treader.Close() //H\n",
        "non-200 branch",
    )
    d = swap(
        d,
        "\taccess sync.Mutex\n\treader io.ReadCloser\n\tclosed bool\n}\n",
        "\taccess sync.Mutex\n\treader io.ReadCloser\n\tclosed bool\n\tfailure error //UralVPN: why the stream ended\n}\n",
        "struct",
    )
    d = swap(
        d,
        "\treader := w.reader\n\tw.access.Unlock()\n\tif reader == nil {\n\t\treturn 0, io.ErrClosedPipe\n\t}\n\treturn reader.Read(b)\n}\n",
        "\treader := w.reader\n\tfailure := w.failure\n\tw.access.Unlock()\n\tif reader == nil {\n\t\tif failure != nil {\n\t\t\treturn 0, failure\n\t\t}\n\t\treturn 0, io.ErrClosedPipe\n\t}\n"
        "\tn, err := reader.Read(b)\n\tif err != nil {\n\t\tw.access.Lock()\n\t\tfailure = w.failure\n\t\tw.access.Unlock()\n\t\tif failure != nil {\n\t\t\treturn n, failure\n\t\t}\n\t}\n\treturn n, err\n}\n\n"
        "// abort records why the stream ends and closes it; Read then returns this error.\n"
        "func (w *WaitReadCloser) abort(err error) {\n\tw.access.Lock()\n\tif w.failure == nil {\n\t\tw.failure = err\n\t}\n\tw.access.Unlock()\n\tw.Close()\n}\n",
        "Read",
    )
    open(dialer, "w").write(d)
    print("xhttp status: dialer patched")

c = open(client).read()
if "w.abort(E.Cause(err" in c:
    print("xhttp status: client already patched")
else:
    c = swap(
        c,
        "\t\t\t\twroteRequest.Close()\n\t\t\t\tif err != nil {\n\t\t\t\t\tuploadPipeReader.Interrupt()\n\t\t\t\t}\n",
        "\t\t\t\twroteRequest.Close()\n\t\t\t\tif err != nil {\n\t\t\t\t\tuploadPipeReader.Interrupt()\n"
        "\t\t\t\t\tif w, ok := conn.reader.(*WaitReadCloser); ok { //UralVPN: the reason of a failed upload is visible in the log\n"
        "\t\t\t\t\t\tw.abort(E.Cause(err, \"xhttp upload\"))\n\t\t\t\t\t}\n\t\t\t\t}\n",
        "upload error",
    )
    open(client, "w").write(c)
    print("xhttp status: client patched")

# The download (GET) request of an XHTTP connection must not carry "Content-Type: application/grpc". The hiddify port
# sets it whenever the request method equals the uplink method, so with "uplinkHTTPMethod": "GET" the plain download
# GET gets it too, and a CDN may treat such a request as gRPC. Only a POST upload carries it.
d = open(dialer).read()
old = "if method == c.options.GetNormalizedUplinkHTTPMethod() && !c.options.NoGRPCHeader {"
if old not in d:
    print("xhttp grpc header: already patched")
else:
    assert d.count(old) == 2 or d.count(old) == 1, "grpc header condition: %d" % d.count(old)
    # only the OpenStream one (the PostPacket code has no such header)
    d = d.replace(old, 'if method == "POST" && !c.options.NoGRPCHeader { //UralVPN: not on a plain download GET')
    open(dialer, "w").write(d)
    print("xhttp grpc header: patched")
