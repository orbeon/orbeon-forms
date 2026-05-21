#!/usr/bin/env python3
"""
Walk a docker/scout-reports/ tree, parse every Docker Scout SARIF file, and
produce two Markdown summaries:

  summary.md       — internal triage view (per-image counts + every CVE)
  github-issue.md  — paste-ready for a GitHub issue (per-image counts table
                    + Critical/High CVE-keyed table)

Usage: scout-summarize.py <reports-dir>
"""

import json
import sys
import subprocess
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import unquote, urlparse

SEVERITIES = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "UNSPECIFIED"]


def parse_purl(purl: str) -> str:
    """Reduce a purl (pkg:maven/io.netty/netty-codec-http@4.1.132.Final?...) to
    a short human label (e.g. "io.netty/netty-codec-http 4.1.132.Final")."""
    if not purl:
        return "?"
    s = purl
    if s.startswith("pkg:"):
        s = s[4:]
    s = s.split("?", 1)[0]
    parts = s.split("/", 1)
    ptype = parts[0]
    rest = parts[1] if len(parts) > 1 else ""
    if "@" in rest:
        name, version = rest.rsplit("@", 1)
        version = unquote(version)
    else:
        name, version = rest, ""
    name = unquote(name)
    if ptype in ("deb", "apk", "rpm") and "/" in name:
        name = name.split("/", 1)[1]
    return f"{name} {version}".strip() or "?"


def parse_sarif(path: Path):
    """Return a list of finding dicts for one SARIF file."""
    with path.open() as f:
        data = json.load(f)
    findings = []
    runs = data.get("runs", [])
    if not runs:
        return findings
    run = runs[0]
    driver = run.get("tool", {}).get("driver", {})
    rules = driver.get("rules", [])

    # Build a map of ruleId -> set of file paths from results
    paths_by_rule = {}
    for result in run.get("results", []):
        rid = result.get("ruleId")
        if not rid:
            continue
        bucket = paths_by_rule.setdefault(rid, set())
        for loc in result.get("locations", []):
            uri = loc.get("physicalLocation", {}).get("artifactLocation", {}).get("uri", "")
            if uri:
                bucket.add(uri)

    for rule in rules:
        cve = rule.get("id", "?")
        props = rule.get("properties", {})
        severity = (props.get("cvssV3_severity") or "UNSPECIFIED").upper()
        fix = props.get("fixed_version") or "not fixed"
        purls = props.get("purls") or []
        package = parse_purl(purls[0]) if purls else "?"
        help_uri = rule.get("helpUri", "")
        if help_uri:
            help_uri = help_uri.split("?", 1)[0]
        locations = paths_by_rule.get(cve, set())
        in_war = any("WEB-INF/lib" in p for p in locations)
        source = "WAR" if in_war else "base"
        findings.append({
            "cve": cve,
            "severity": severity,
            "package": package,
            "fix": fix,
            "help_uri": help_uri,
            "rule_name": rule.get("name", ""),
            "source": source,
            "locations": sorted(locations),
        })
    return findings


def image_key(repo_slug: str, tag: str, platform: str) -> str:
    repo = repo_slug.replace("_", "/", 1)
    return f"{repo}:{tag} ({platform})"


def collect(reports_dir: Path):
    """Walk reports_dir/<repo_slug>/<tag>__<platform>.sarif files. Return:
       per_image: dict[image_key] -> {severity: count, total: int}
       per_cve:   dict[cve] -> {severity, package, fix, help_uri, images: set}
    """
    per_image = {}
    per_cve = {}

    for repo_dir in sorted(p for p in reports_dir.iterdir() if p.is_dir()):
        if repo_dir.name.startswith("_") or repo_dir.name == "publish":
            continue
        for sarif in sorted(repo_dir.glob("*.sarif")):
            name = sarif.stem  # tag__platform-slug
            if "__" not in name:
                continue
            tag, platform_slug = name.rsplit("__", 1)
            platform = platform_slug.replace("-", "/", 1)
            key = image_key(repo_dir.name, tag, platform)
            counts = {s: 0 for s in SEVERITIES}
            base_counts = {s: 0 for s in SEVERITIES}
            war_counts = {s: 0 for s in SEVERITIES}
            try:
                findings = parse_sarif(sarif)
            except (json.JSONDecodeError, OSError):
                per_image[key] = {**counts, "base": base_counts, "war": war_counts,
                                  "total": 0, "error": "parse_failed"}
                continue
            for f in findings:
                sev = f["severity"] if f["severity"] in SEVERITIES else "UNSPECIFIED"
                counts[sev] += 1
                if f["source"] == "WAR":
                    war_counts[sev] += 1
                else:
                    base_counts[sev] += 1
                cve = f["cve"]
                if cve not in per_cve:
                    per_cve[cve] = {
                        "severity": sev,
                        "package": f["package"],
                        "fix": f["fix"],
                        "help_uri": f["help_uri"],
                        "source": f["source"],
                        "images": set(),
                    }
                per_cve[cve]["images"].add(key)
            per_image[key] = {**counts, "base": base_counts, "war": war_counts,
                              "total": sum(counts.values())}
    return per_image, per_cve


def split_image_key(key: str):
    repo_tag, _, plat = key.rpartition(" (")
    plat = plat.rstrip(")")
    repo, _, tag = repo_tag.partition(":")
    return repo, tag, plat


def sort_image_keys(keys):
    """Sort by (repo asc, tag desc, platform asc) using stable multi-pass."""
    items = sorted(keys, key=lambda k: split_image_key(k)[2])           # platform asc
    items.sort(key=lambda k: split_image_key(k)[1], reverse=True)        # tag desc
    items.sort(key=lambda k: split_image_key(k)[0])                      # repo asc
    return items


def md_table(headers, rows):
    out = ["| " + " | ".join(headers) + " |",
           "|" + "|".join("---" for _ in headers) + "|"]
    for row in rows:
        out.append("| " + " | ".join(str(c) for c in row) + " |")
    return "\n".join(out)


def write_summary(reports_dir: Path, per_image, per_cve, scout_version: str):
    lines = []
    lines.append("# Docker image vulnerability scan — full triage view\n")
    lines.append(f"_Generated: {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')}_  ")
    lines.append(f"_Scanner: Docker Scout {scout_version}_  ")
    lines.append(f"_Images scanned: {len(per_image)}_  ")
    lines.append(f"_Unique CVEs: {len(per_cve)}_\n")

    lines.append("## Per-image counts\n")
    lines.append("Cells show `base / WAR` — base-image CVEs vs CVEs inside `orbeon.war`.\n")
    rows = []
    for key in sort_image_keys(per_image.keys()):
        c = per_image[key]
        crit = f"{c['base']['CRITICAL']} / {c['war']['CRITICAL']}"
        high = f"{c['base']['HIGH']} / {c['war']['HIGH']}"
        med  = f"{c['base']['MEDIUM']} / {c['war']['MEDIUM']}"
        low  = f"{c['base']['LOW']} / {c['war']['LOW']}"
        rows.append([key, crit, high, med, low, c["total"]])
    lines.append(md_table(
        ["Image", "Crit (b/W)", "High (b/W)", "Med (b/W)", "Low (b/W)", "Total"], rows))
    lines.append("")

    lines.append("## All CVEs (by severity, then source, then CVE)\n")
    severity_rank = {s: i for i, s in enumerate(SEVERITIES)}
    source_rank = {"base": 0, "WAR": 1}
    cve_rows = list(per_cve.items())
    cve_rows.sort(key=lambda r: (
        severity_rank.get(r[1]["severity"], 99),
        source_rank.get(r[1].get("source", "base"), 99),
        r[0]))
    rows = []
    for cve, info in cve_rows:
        link = f"[{cve}]({info['help_uri']})" if info["help_uri"] else cve
        affected = ", ".join(sort_image_keys(info["images"]))
        rows.append([link, info["severity"], info.get("source", "base"),
                     info["package"], info["fix"], affected])
    lines.append(md_table(
        ["CVE", "Severity", "Source", "Package", "Fixed version", "Affected images"], rows))
    lines.append("")

    (reports_dir / "summary.md").write_text("\n".join(lines))


def write_github_issue(reports_dir: Path, per_image, per_cve, scout_version: str):
    lines = []
    total_images = len(per_image)
    total_cves = sum(1 for v in per_cve.values() if v["severity"] in ("CRITICAL", "HIGH"))
    lines.append("# Docker image vulnerabilities — Critical & High\n")
    lines.append(f"Scan date: **{datetime.now(timezone.utc).strftime('%Y-%m-%d')}**  ")
    lines.append(f"Scanner: **Docker Scout {scout_version}**  ")
    lines.append(f"Images scanned: **{total_images}** ({len(set(k.split(' (')[0] for k in per_image))} tags × 2 platforms)  ")
    lines.append(f"Unique Critical+High CVEs: **{total_cves}**\n")
    lines.append("Generated by [`docker/scan-vulnerabilities.sh`](../docker/scan-vulnerabilities.sh). Full SARIF + per-image reports live under `docker/scout-reports/` (untracked).\n")

    lines.append("## Per-image counts\n")
    lines.append("`base` = fixable by bumping base image (`FROM` tag).  ")
    lines.append("`WAR` = inside `orbeon.war`; requires a new patch release.\n")
    rows = []
    for key in sort_image_keys(per_image.keys()):
        c = per_image[key]
        repo, tag, plat = split_image_key(key)
        crit = f"{c['base']['CRITICAL']} / {c['war']['CRITICAL']}"
        high = f"{c['base']['HIGH']} / {c['war']['HIGH']}"
        med  = f"{c['base']['MEDIUM']} / {c['war']['MEDIUM']}"
        low  = f"{c['base']['LOW']} / {c['war']['LOW']}"
        rows.append([repo, tag, plat, crit, high, med, low])
    lines.append(md_table(
        ["Image", "Tag", "Platform",
         "Crit (base / WAR)", "High (base / WAR)", "Med (base / WAR)", "Low (base / WAR)"],
        rows))
    lines.append("")

    lines.append("## Critical & High CVEs\n")
    severity_rank = {s: i for i, s in enumerate(SEVERITIES)}
    source_rank = {"base": 0, "WAR": 1}
    rows = []
    high_crit = [(cve, info) for cve, info in per_cve.items()
                 if info["severity"] in ("CRITICAL", "HIGH")]
    high_crit.sort(key=lambda r: (
        severity_rank.get(r[1]["severity"], 99),
        source_rank.get(r[1].get("source", "base"), 99),
        r[0]))
    for cve, info in high_crit:
        link = f"[{cve}]({info['help_uri']})" if info["help_uri"] else cve
        affected = ", ".join(sort_image_keys(info["images"]))
        rows.append([link, info["severity"], info.get("source", "base"),
                     info["package"], info["fix"], affected])
    if rows:
        lines.append(md_table(
            ["CVE", "Severity", "Source", "Package", "Fixed version", "Affected images"], rows))
    else:
        lines.append("_No Critical or High CVEs found across all scanned images._")
    lines.append("")

    (reports_dir / "github-issue.md").write_text("\n".join(lines))


def detect_scout_version() -> str:
    try:
        out = subprocess.run(
            ["docker", "scout", "version"], capture_output=True, text=True, timeout=10
        ).stdout
        for line in out.splitlines():
            stripped = line.strip()
            if stripped.startswith("version:"):
                return stripped.split(":", 1)[1].strip()
    except (OSError, subprocess.SubprocessError):
        pass
    return "unknown"


def main(argv):
    if len(argv) != 2:
        print("Usage: scout-summarize.py <reports-dir>", file=sys.stderr)
        return 2
    reports_dir = Path(argv[1]).resolve()
    if not reports_dir.is_dir():
        print(f"Not a directory: {reports_dir}", file=sys.stderr)
        return 2
    per_image, per_cve = collect(reports_dir)
    scout_version = detect_scout_version()
    write_summary(reports_dir, per_image, per_cve, scout_version)
    write_github_issue(reports_dir, per_image, per_cve, scout_version)
    print(f"Wrote {reports_dir/'summary.md'}")
    print(f"Wrote {reports_dir/'github-issue.md'}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
