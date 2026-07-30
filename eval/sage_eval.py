"""
sage_eval.py — Sage AI evaluation harness with MLflow tracking.

Phase 1 tasks covered:
  T2 — MLflow run lifecycle + parameter logging
  T3 — Wall-clock latency, success/failure, gap rate, P50/P95 (eval loop)
  T5 — Artifact logging (eval_results.json + sage_config_snapshot.json)

Usage:
  python eval/sage_eval.py [--top-k N] [--questions PATH] [--sage-url URL]

Requirements: pip install -r eval/requirements.txt
MLflow server must be running at http://localhost:5000 (or MLFLOW_TRACKING_URI env var).
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import tempfile
import time
import warnings
from typing import Any

import numpy as np
import requests

# ── MLflow import — fail-soft ────────────────────────────────────────────────
try:
    import mlflow

    MLFLOW_OK = True
except ImportError:
    warnings.warn("mlflow not installed — metrics will not be logged", stacklevel=1)
    MLFLOW_OK = False

# ── Constants ────────────────────────────────────────────────────────────────
EXPERIMENT_NAME = "sage-ai-agent-evaluation"
DEFAULT_SAGE_URL = os.getenv("SAGE_URL", "http://localhost:8080")
DEFAULT_MLFLOW_URI = os.getenv("MLFLOW_TRACKING_URI", "http://localhost:5000")
DEFAULT_QUESTIONS_PATH = os.path.join(os.path.dirname(__file__), "questions.json")


# ── Helpers ──────────────────────────────────────────────────────────────────

def _git_commit() -> str:
    """Return the short HEAD commit hash, or 'unknown' on failure."""
    try:
        return (
            subprocess.check_output(
                ["git", "rev-parse", "--short", "HEAD"],
                stderr=subprocess.DEVNULL,
            )
            .decode()
            .strip()
        )
    except Exception:
        return "unknown"


def _hardware() -> str:
    """Return a short hardware descriptor for the param log."""
    import platform
    return f"{platform.system()}-{platform.machine()}"


def _setup_mlflow(tracking_uri: str) -> bool:
    """
    Try to connect to MLflow and create/get the experiment.
    Returns True on success, False if MLflow is unreachable (fail-soft).
    """
    if not MLFLOW_OK:
        return False
    try:
        mlflow.set_tracking_uri(tracking_uri)
        mlflow.set_experiment(EXPERIMENT_NAME)
        # Lightweight connectivity check
        mlflow.MlflowClient().search_experiments(max_results=1)
        return True
    except Exception as exc:
        warnings.warn(
            f"MLflow unreachable at {tracking_uri} — metrics will not be logged. "
            f"Sage /ask is unaffected. ({exc})",
            stacklevel=1,
        )
        return False


# ── SSE parser ───────────────────────────────────────────────────────────────

def _parse_sse_stream(response: requests.Response) -> dict[str, Any]:
    """
    Consume a streaming SSE response from POST /ask.

    Returns a dict with keys:
      answer, gap_flag, llm_ms, retrieval_ms, e2e_ms, error
    All values default to None if the event was absent.
    """
    result: dict[str, Any] = {
        "answer": None,
        "gap_flag": None,
        "llm_ms": None,
        "retrieval_ms": None,
        "e2e_ms": None,
        "input_tokens": None,
        "output_tokens": None,
        "tokens_per_sec": None,
        "error": None,
    }

    event_name: str | None = None
    data_buf: list[str] = []

    for raw_line in response.iter_lines(decode_unicode=True):
        if raw_line is None:
            continue
        line = raw_line.strip()

        if line.startswith("event:"):
            event_name = line[len("event:"):].strip()
            data_buf = []
        elif line.startswith("data:"):
            data_buf.append(line[len("data:"):].strip())
        elif line == "":
            # Dispatch accumulated event
            if event_name:
                if data_buf:
                    raw_data = "".join(data_buf)
                    try:
                        payload = json.loads(raw_data)
                    except json.JSONDecodeError:
                        payload = raw_data

                    if event_name == "result" and isinstance(payload, dict):
                        result["answer"] = payload.get("answer") or payload.get("directAnswer")
                        result["gap_flag"] = bool(payload.get("gapFlag", False))

                    elif event_name == "timing" and isinstance(payload, dict):
                        result["e2e_ms"] = payload.get("e2e_ms")
                        result["llm_ms"] = payload.get("llm_ms")
                        result["retrieval_ms"] = payload.get("retrieval_ms")
                        result["input_tokens"] = payload.get("input_tokens")
                        result["output_tokens"] = payload.get("output_tokens")
                        result["tokens_per_sec"] = payload.get("tokens_per_sec")

                    elif event_name == "error":
                        result["error"] = (
                            payload.get("message") if isinstance(payload, dict) else str(payload)
                        )

                if event_name in ("done", "error"):
                    break

            event_name = None
            data_buf = []

    return result


# ── Evaluation loop ──────────────────────────────────────────────────────────

def run_evaluation(
    questions: list[dict],
    sage_url: str,
    top_k: int,
    mlflow_ok: bool,
) -> tuple[list[dict], dict[str, float]]:
    """
    Call POST /ask for each question, collect metrics.

    Returns:
      (per_request_rows, aggregate_metrics)
    """
    rows: list[dict] = []
    latencies: list[float] = []
    success_count = 0
    gap_count = 0

    for idx, q in enumerate(questions):
        question_text = q.get("question", q.get("q", ""))
        print(f"  [{idx + 1}/{len(questions)}] {question_text[:80]}", flush=True)

        t0 = time.perf_counter()
        success = False
        gap_flag = None
        llm_ms = None
        retrieval_ms = None
        input_tokens = None
        output_tokens = None
        tokens_per_sec = None
        error_msg = None

        try:
            resp = requests.post(
                f"{sage_url}/ask",
                json={"query": question_text},
                headers={"Accept": "text/event-stream", "Content-Type": "application/json"},
                stream=True,
                timeout=120,
            )
            resp.raise_for_status()

            sse = _parse_sse_stream(resp)
            resp.close()
            elapsed = time.perf_counter() - t0

            if sse["error"]:
                error_msg = sse["error"]
            else:
                success = True
                gap_flag = sse.get("gap_flag")
                llm_ms = sse.get("llm_ms")
                retrieval_ms = sse.get("retrieval_ms")
                input_tokens = sse.get("input_tokens")
                output_tokens = sse.get("output_tokens")
                tokens_per_sec = sse.get("tokens_per_sec")

        except Exception as exc:
            elapsed = time.perf_counter() - t0
            error_msg = str(exc)
            print(f"    [x] Error: {error_msg}", flush=True)

        if success:
            success_count += 1
            latencies.append(elapsed)
            if gap_flag:
                gap_count += 1

        row = {
            "question": question_text,
            "latency_s": round(elapsed, 3),
            "success": success,
            "gap_flag": gap_flag,
            "llm_ms": llm_ms,
            "retrieval_ms": retrieval_ms,
            "input_tokens": input_tokens,
            "output_tokens": output_tokens,
            "tokens_per_sec": tokens_per_sec,
            "error": error_msg,
        }
        rows.append(row)

        # ── Per-step MLflow metrics ──────────────────────────────────────────
        if mlflow_ok:
            mlflow.log_metric("e2e_latency_s", elapsed, step=idx)
            if llm_ms is not None:
                mlflow.log_metric("llm_latency_s", llm_ms / 1000.0, step=idx)
            if retrieval_ms is not None:
                mlflow.log_metric("retrieval_latency_s", retrieval_ms / 1000.0, step=idx)
            if input_tokens is not None:
                mlflow.log_metric("input_tokens", float(input_tokens), step=idx)
            if output_tokens is not None:
                mlflow.log_metric("output_tokens", float(output_tokens), step=idx)
            if tokens_per_sec is not None:
                mlflow.log_metric("tokens_per_sec", float(tokens_per_sec), step=idx)

    total = len(questions)
    success_rate = success_count / total if total else 0.0
    gap_rate = gap_count / success_count if success_count else 0.0

    agg: dict[str, float] = {
        "total_requests": float(total),
        "success_rate": round(success_rate, 4),
        "failed_count": float(total - success_count),
        "gap_rate": round(gap_rate, 4),
    }

    if len(latencies) >= 5:
        agg["p50_latency_s"] = round(float(np.percentile(latencies, 50)), 3)
        agg["p95_latency_s"] = round(float(np.percentile(latencies, 95)), 3)

    return rows, agg


# ── Artifact helpers ─────────────────────────────────────────────────────────

def _log_artifacts(rows: list[dict], params: dict) -> None:
    """Log eval_results.json and sage_config_snapshot.json as MLflow artifacts."""
    with tempfile.TemporaryDirectory() as tmp:
        results_path = os.path.join(tmp, "eval_results.json")
        with open(results_path, "w", encoding="utf-8") as f:
            json.dump(rows, f, indent=2)
        mlflow.log_artifact(results_path)

        config_path = os.path.join(tmp, "sage_config_snapshot.json")
        with open(config_path, "w", encoding="utf-8") as f:
            json.dump(params, f, indent=2)
        mlflow.log_artifact(config_path)


# ── Entry point ──────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="Sage AI evaluation harness")
    parser.add_argument("--sage-url", default=DEFAULT_SAGE_URL)
    parser.add_argument("--mlflow-uri", default=DEFAULT_MLFLOW_URI)
    parser.add_argument("--top-k", type=int, default=5)
    parser.add_argument("--questions", default=DEFAULT_QUESTIONS_PATH)
    args = parser.parse_args()

    # ── Load questions ───────────────────────────────────────────────────────
    if not os.path.exists(args.questions):
        print(f"ERROR: questions file not found: {args.questions}", file=sys.stderr, flush=True)
        sys.exit(1)
    with open(args.questions, encoding="utf-8") as f:
        questions = json.load(f)
    print(f"Loaded {len(questions)} questions from {args.questions}", flush=True)

    # ── Parameters (T2 — logged to every run) ───────────────────────────────
    params = {
        "llm_model": "llama3.1:8b",
        "git_commit": _git_commit(),
        "retrieval_top_k": args.top_k,
        "retrieval_min_score": 0.5,
        "scoring_w1": 0.6,
        "scoring_w2": 0.4,
        "scoring_dual_boost": 1.2,
        "hardware": _hardware(),
        "prompt_version": "v1",
    }

    # ── Ensure UTF-8 stdout encoding on Windows to prevent UnicodeEncodeError in MLflow SDK
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    # ── Connect to MLflow (fail-soft) ────────────────────────────────────────
    mlflow_ok = _setup_mlflow(args.mlflow_uri)
    if mlflow_ok:
        print(f"MLflow connected -> {args.mlflow_uri}  experiment: {EXPERIMENT_NAME}", flush=True)
    else:
        print("WARNING: MLflow unavailable -- running eval without metric logging", flush=True)

    # ── Main run ─────────────────────────────────────────────────────────────
    ctx = mlflow.start_run() if mlflow_ok else _null_context()
    run_status = "FINISHED"
    with ctx:
        try:
            if mlflow_ok:
                mlflow.log_params(params)

            print(f"\nRunning {len(questions)} questions against {args.sage_url} ...\n", flush=True)
            rows, agg = run_evaluation(questions, args.sage_url, args.top_k, mlflow_ok)

            if mlflow_ok:
                mlflow.log_metrics(agg)
                _log_artifacts(rows, params)
                run_id = mlflow.active_run().info.run_id
                print(f"\nMLflow run logged: {run_id}", flush=True)

            print("\n-- Aggregate results -----------------------------------------", flush=True)
            for k, v in agg.items():
                print(f"  {k}: {v}", flush=True)

        except Exception as exc:
            run_status = "FAILED"
            print(f"\n[x] Run failed: {exc}", flush=True)
            raise
        finally:
            if mlflow_ok and mlflow.active_run():
                mlflow.end_run(status=run_status)


class _null_context:
    """No-op context manager used when MLflow is unavailable."""
    def __enter__(self): return self
    def __exit__(self, *_): pass


if __name__ == "__main__":
    main()
