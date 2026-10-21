"""Bounded live Responses/SSE check. Credentials are read only from the environment."""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone, timedelta
import gzip
import http.client
import json
import os
from pathlib import Path
import socket
import ssl
import threading
import time
from urllib.parse import urlsplit
import uuid


PRINT_LOCK = threading.Lock()


def emit(item):
    with PRINT_LOCK:
        print(json.dumps(item, ensure_ascii=True), flush=True)


def run_request(args, index, barrier, key):
    request_id = f"relay-fresh-{uuid.uuid4()}"
    marker = f"FRESH-{index}-{uuid.uuid4().hex[:8]}"
    payload = {
        "model": args.model,
        "stream": True,
        "store": False,
        "reasoning": {"effort": args.effort},
        "max_output_tokens": 4096,
        "input": [{"role": "user", "content": (
            f"This is a new independent conversation. Start your answer with {marker}. "
            "Write a self-contained Python example, about 40 lines, to parse timestamped "
            "server logs and report per-minute request counts, successes, failures and "
            "average latency. Explain the example briefly in Chinese. Use only the "
            "Python standard library. Keep the answer under 800 Chinese characters "
            "excluding code."
        )}],
    }
    encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {
        "Authorization": "Bearer " + key,
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
        "User-Agent": "imageCreater-live-stream-diagnostic/20261014",
        "x-client-request-id": request_id,
    }
    if args.encoding == "gzip":
        encoded = gzip.compress(encoded)
        headers["Content-Encoding"] = "gzip"
    target = urlsplit(args.base_url.rstrip("/") + "/responses")
    if target.scheme != "https" or target.username or target.password:
        raise ValueError("An HTTPS base URL without embedded credentials is required")
    barrier.wait(timeout=15)
    started = time.monotonic()
    result = {
        "index": index, "request_id": request_id,
        "started_at": datetime.now(timezone(timedelta(hours=8))).isoformat(),
        "encoding": args.encoding, "effort": args.effort, "marker": marker,
        "status": "running", "http_status": None, "first_text_seconds": None,
        "first_event_seconds": None, "response_id": None,
    }
    conn = http.client.HTTPSConnection(target.hostname, target.port or 443,
                                      timeout=30, context=ssl.create_default_context())
    transport = None
    expired = threading.Event()
    timer = None
    response = None
    counts = Counter()
    output = []
    last_progress = started

    def redact(value):
        return str(value).replace(key, "[REDACTED]")[:400]

    def expire():
        expired.set()
        if transport is not None:
            try:
                transport.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
        conn.close()

    def process(lines):
        if not lines:
            return False
        now = time.monotonic()
        if result["first_event_seconds"] is None:
            result["first_event_seconds"] = round(now - started, 3)
        event = ""
        data = []
        for line in lines:
            if line.startswith("event:"):
                event = line[6:].strip()
            elif line.startswith("data:"):
                data.append(line[5:].lstrip(" "))
        if not data:
            counts["comment_heartbeat"] += 1
            return False
        raw = "\n".join(data)
        if raw == "[DONE]":
            counts["done_marker"] += 1
            return False  # Responses still requires response.completed.
        try:
            body = json.loads(raw)
        except json.JSONDecodeError:
            counts["invalid_json_event"] += 1
            return False
        kind = body.get("type") or event or "unknown"
        counts[kind] += 1
        info = body.get("response")
        if isinstance(info, dict):
            result["response_id"] = info.get("id", result["response_id"])
        if kind == "response.output_text.delta":
            delta = body.get("delta", "")
            if delta:
                if result["first_text_seconds"] is None:
                    result["first_text_seconds"] = round(now - started, 3)
                    emit({"request_id": request_id, "phase": "first_text",
                          "seconds": result["first_text_seconds"]})
                output.append(delta)
            else:
                counts["empty_text_delta"] += 1
        if kind in {"response.completed", "response.failed", "response.incomplete", "error"}:
            result["terminal_event"] = kind
            result["status"] = "completed" if kind == "response.completed" else kind
            if isinstance(info, dict):
                result["usage"] = info.get("usage")
                if info.get("error"):
                    result["error"] = redact(json.dumps(info["error"], ensure_ascii=False))
                if info.get("incomplete_details"):
                    result["incomplete_details"] = info["incomplete_details"]
            if kind == "error":
                result["error"] = redact(json.dumps(body.get("error", body), ensure_ascii=False))
            return True
        return False

    emit({"request_id": request_id, "phase": "start", "index": index,
          "encoding": args.encoding, "effort": args.effort})
    try:
        conn.connect()
        transport = conn.sock
        transport.settimeout(args.timeout)
        timer = threading.Timer(max(1, args.timeout - (time.monotonic() - started)), expire)
        timer.daemon = True
        timer.start()
        conn.request("POST", target.path + ("?" + target.query if target.query else ""),
                     body=encoded, headers=headers)
        response = conn.getresponse()
        result["http_status"] = response.status
        result["downstream_http_version"] = f"HTTP/{response.version // 10}.{response.version % 10}"
        result["headers_seconds"] = round(time.monotonic() - started, 3)
        result["content_type"] = response.getheader("Content-Type", "")
        result["server_request_id"] = response.getheader("x-request-id")
        emit({"request_id": request_id, "phase": "headers", "status": response.status,
              "seconds": result["headers_seconds"], "content_type": result["content_type"]})
        if not 200 <= response.status < 300 or "text/event-stream" not in result["content_type"].lower():
            result["status"] = "http_error" if response.status >= 300 else "unexpected_content_type"
            result["error"] = redact(response.read(2048).decode("utf-8", errors="replace"))
        else:
            frame = []
            frame_size = 0
            while True:
                raw = response.readline(1024 * 1024)
                if not raw:
                    if frame:
                        process(frame)
                    if result["status"] == "running":
                        result["status"] = "eof_without_terminal"
                    break
                frame_size += len(raw)
                if frame_size > 4 * 1024 * 1024:
                    raise ValueError("SSE frame exceeded diagnostic size bound")
                line = raw.decode("utf-8", errors="replace").rstrip("\r\n")
                if not line:
                    terminal = process(frame)
                    frame = []
                    frame_size = 0
                    if terminal:
                        break
                else:
                    frame.append(line)
                now = time.monotonic()
                if now - last_progress >= 30:
                    emit({"request_id": request_id, "phase": "progress",
                          "seconds": round(now - started, 1),
                          "text_chars": sum(map(len, output)), "events": dict(counts)})
                    last_progress = now
    except Exception as ex:
        result["status"] = "test_deadline" if expired.is_set() else "transport_error"
        result["error"] = redact(f"{type(ex).__name__}: {ex}")
    finally:
        if timer is not None:
            timer.cancel()
        if response is not None:
            response.close()
        conn.close()
    if expired.is_set():
        result["status"] = "test_deadline"
    result["elapsed_seconds"] = round(time.monotonic() - started, 3)
    result["events"] = dict(counts)
    text = "".join(output)
    result["output_chars"] = len(text)
    result["marker_present"] = marker in text
    result["output_preview"] = redact(text[:160])
    emit({"request_id": request_id, "phase": "end", "status": result["status"],
          "seconds": result["elapsed_seconds"], "text_chars": len(text)})
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--concurrency", type=int, default=1, choices=range(1, 6))
    parser.add_argument("--effort", choices=["low", "medium", "high", "xhigh"], default="high")
    parser.add_argument("--encoding", choices=["identity", "gzip"], default="identity")
    parser.add_argument("--timeout", type=int, default=360)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    key = os.environ.get("RELAY_LIVE_TEST_KEY", "").strip()
    if not key:
        raise SystemExit("RELAY_LIVE_TEST_KEY is required; do not put it in script files")
    barrier = threading.Barrier(args.concurrency)
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = [pool.submit(run_request, args, i, barrier, key) for i in range(args.concurrency)]
        results = [future.result() for future in futures]
    report = {"base_url": args.base_url, "model": args.model,
              "concurrency": args.concurrency, "results": results}
    target = Path(args.output)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    emit({"report": str(target), "counts": dict(Counter(r["status"] for r in results))})


if __name__ == "__main__":
    main()
