#!/usr/bin/env python3
"""Audit des dépendances (1.21) : interroge la base publique OSV (api.osv.dev) pour chaque bibliothèque Android fixée dans
android/gradle/libs.versions.toml (Maven) et chaque paquet npm des Edge Functions (`npm:nom@version`).
Seuls des noms et versions de bibliothèques publiques sont envoyés. Code de sortie 1 si une vulnérabilité connue est trouvée."""
import glob
import json
import re
import sys
import urllib.request

def maven(path):
    t = open(path).read()
    vers = dict(re.findall(r'^([\w-]+)\s*=\s*"([^"]+)"', t.split("[libraries]")[0].split("[versions]")[1], re.M))
    out = []
    for m in re.finditer(r'^[\w-]+\s*=\s*\{([^}]*)\}', t.split("[libraries]")[1].split("[plugins]")[0], re.M):
        body = m.group(1)
        mod = re.search(r'module\s*=\s*"([^"]+)"', body)
        grp, name = re.search(r'group\s*=\s*"([^"]+)"', body), re.search(r'name\s*=\s*"([^"]+)"', body)
        ref, raw = re.search(r'version\.ref\s*=\s*"([^"]+)"', body), re.search(r'version\s*=\s*"([^"]+)"', body)
        coord = mod.group(1) if mod else (f"{grp.group(1)}:{name.group(1)}" if grp and name else None)
        ver = vers.get(ref.group(1)) if ref else (raw.group(1) if raw else None)
        if coord and ver:
            out.append(("Maven", coord, ver))
    return out

def npm(pattern):
    out = set()
    for f in glob.glob(pattern):
        for name, ver in re.findall(r'npm:(@?[\w./-]+?)@(\d[\w.-]*)', open(f).read()):
            out.add(("npm", name, ver))
    return sorted(out)

deps = maven("android/gradle/libs.versions.toml") + npm("supabase/functions/*/index.ts")
query = {"queries": [{"package": {"ecosystem": e, "name": n}, "version": v} for e, n, v in deps]}
req = urllib.request.Request("https://api.osv.dev/v1/querybatch", data=json.dumps(query).encode(),
                             headers={"Content-Type": "application/json"})
results = json.load(urllib.request.urlopen(req, timeout=60))["results"]
found = [(d, [v["id"] for v in r.get("vulns", [])]) for d, r in zip(deps, results) if r.get("vulns")]
print(f"{len(deps)} dépendances vérifiées, {len(found)} avec une vulnérabilité connue.")
for (eco, name, ver), ids in found:
    print(f"  {eco} {name} {ver} : {', '.join(ids)}")
sys.exit(1 if found else 0)
