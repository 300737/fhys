#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""轮询等待指定 run 完成，并打印 Release 资产（CF 免 token 直链）"""
import sys
import time

from ci_status import api, REPO

RUN = int(sys.argv[1]) if len(sys.argv) > 1 else None
MAX = int(sys.argv[2]) if len(sys.argv) > 2 else 40  # 40 * 45s ≈ 30min

CF = "https://cf.612526.xyz/https://github.com"


def find_run(no):
    d = api(f"/repos/{REPO}/actions/runs?per_page=10") or {}
    for r in d.get("workflow_runs", []):
        if r["run_number"] == no:
            return r
    return None


def main():
    run = find_run(RUN) if RUN else (api(f"/repos/{REPO}/actions/runs?per_page=1") or {}).get("workflow_runs", [None])[0]
    if not run:
        print("找不到 run", RUN)
        return
    rid, no = run["id"], run["run_number"]
    print(f"等待 #{no} (id={rid}) sha={run['head_sha'][:7]}", flush=True)
    for i in range(MAX):
        cur = api(f"/repos/{REPO}/actions/runs/{rid}")
        if not cur:
            continue
        st, con = cur.get("status"), cur.get("conclusion")
        if st == "completed":
            print(f"\n#{no} 结束: {con}", flush=True)
            jobs = api(f"/repos/{REPO}/actions/runs/{rid}/jobs?per_page=20") or {}
            for j in jobs.get("jobs", []):
                print(f"  {j.get('name')}: {j.get('conclusion')}", flush=True)
            rel = api(f"/repos/{REPO}/releases/tags/latest") or {}
            print("\nRelease:", rel.get("name"), flush=True)
            for a in rel.get("assets", []):
                print("  -", a["name"], a.get("size"), flush=True)
                print("    " + f"{CF}/300737/fhys/releases/download/latest/" + a["name"], flush=True)
            return
        print(f"  [{i}] {st} ...", flush=True)
        time.sleep(45)
    print("超时仍未结束")


if __name__ == "__main__":
    main()
