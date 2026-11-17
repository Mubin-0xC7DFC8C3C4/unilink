#!/usr/bin/env python3
"""Fetch Maven dependencies with curl (not Java) into the durable local repo.

Why this exists: in this sandbox Java processes cannot make outbound HTTPS
connections -- the egress filter returns a plaintext policy message instead
of an HTTP response ("Other TCP connections is turned off ... other_tcp
Deny"), so Maven itself can never download from Maven Central. curl works
fine through the same egress proxy. This script resolves the dependency
graph from POMs (parents, BOM imports, dependencyManagement, exclusions,
scopes) and downloads every .pom/.jar into ~/.m2/repository, after which
Maven runs offline (-o) with <localRepository>/home/hatch/.m2/repository.

Usage:
  python3 ops/fetch-deps.py [--repo /home/hatch/.m2/repository]
                            [--pom backend/pom.xml]
  Then: mvn -o -s ~/.m2/settings.xml compile   (settings.xml must set
  <localRepository> to the same path and contain the egress proxy.)

Idempotent: already-downloaded artifacts are skipped (checked by file
existence + non-zero size). Safe to re-run; it only adds missing artifacts.
"""
import argparse
import concurrent.futures as cf
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import deque

BASE = "https://repo.maven.apache.org/maven2/"
NS = "{http://maven.apache.org/POM/4.0.0}"


def tag(el, name):
    for child in el:
        if child.tag.replace(NS, "") == name:
            return child
    return None


def text(el, name, default=None):
    c = tag(el, name)
    return (c.text or "").strip() if c is not None and c.text else default


def children(el, name):
    out = []
    if el is None:
        return out
    for child in el:
        if child.tag.replace(NS, "") == name:
            out.append(child)
    return out


class Pom:
    def __init__(self, g, a, v):
        self.g, self.a, self.v = g, a, v
        self.props = {}
        self.dep_mgmt = {}          # (g,a) -> version
        self.plugin_mgmt = {}       # (g,a) -> version
        self.deps = []              # dicts
        self.plugins = []           # dicts
        self.parent = None

    def coord(self):
        return (self.g, self.a, self.v)


class Fetcher:
    def __init__(self, repo):
        self.repo = repo
        self.pom_cache = {}
        self.downloaded = set()

    def path(self, g, a, v, ext):
        return os.path.join(self.repo, *g.split("."), a, v, f"{a}-{v}.{ext}")

    def curl(self, url, dest, retries=3):
        if os.path.exists(dest) and os.path.getsize(dest) > 0:
            return True
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        tmp = dest + ".tmp"
        for attempt in range(retries):
            r = subprocess.run(
                ["curl", "-sf", "--max-time", "90", "-o", tmp, url],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if r.returncode == 0 and os.path.getsize(tmp) > 0:
                os.replace(tmp, dest)
                return True
        if os.path.exists(tmp):
            os.remove(tmp)
        return False

    def fetch_pom(self, g, a, v):
        key = (g, a, v)
        if key in self.pom_cache:
            return self.pom_cache[key]
        dest = self.path(g, a, v, "pom")
        url = f"{BASE}{'/'.join(g.split('.'))}/{a}/{v}/{a}-{v}.pom"
        if not self.curl(url, dest):
            self.pom_cache[key] = None
            return None
        try:
            root = ET.parse(dest).getroot()
        except ET.ParseError:
            self.pom_cache[key] = None
            return None
        pom = Pom(g, a, v)
        self.pom_cache[key] = pom
        parent_el = tag(root, "parent")
        if parent_el is not None:
            pg, pa, pv = (text(parent_el, "groupId"), text(parent_el, "artifactId"),
                          text(parent_el, "version"))
            pom.parent = (pg, pa, pv)
        # properties (own, before parent merge -- parent props are filled in
        # by the resolver when it walks the chain)
        props_el = tag(root, "properties")
        if props_el is not None:
            for c in props_el:
                if c.text:
                    pom.props[c.tag.replace(NS, "")] = c.text.strip()
        # dependencyManagement
        dm = tag(root, "dependencyManagement")
        if dm is not None:
            for d in children(tag(dm, "dependencies"), "dependency"):
                dg, da, dv = text(d, "groupId"), text(d, "artifactId"), text(d, "version")
                scope = text(d, "scope")
                if dg and da:
                    dg = dg.replace("${project.groupId}", g).replace("${project.artifactId}", a)
                    da = da.replace("${project.groupId}", g).replace("${project.artifactId}", a)
                    pom.dep_mgmt[(dg, da)] = (dv, scope)
        # dependencies
        deps_el = tag(root, "dependencies")
        if deps_el is not None:
            for d in children(deps_el, "dependency"):
                dg, da, dv = text(d, "groupId"), text(d, "artifactId"), text(d, "version")
                if not dg or not da:
                    continue
                excl = set()
                ex_el = tag(d, "exclusions")
                if ex_el is not None:
                    for e in children(ex_el, "exclusion"):
                        excl.add((text(e, "groupId"), text(e, "artifactId")))
                pom.deps.append({
                    "g": dg, "a": da, "v": dv, "scope": text(d, "scope") or "compile",
                    "optional": (text(d, "optional") == "true"),
                    "type": text(d, "type") or "jar",
                    "exclusions": excl,
                })
        # pluginManagement + build plugins
        build = tag(root, "build")
        if build is not None:
            pm = tag(build, "pluginManagement")
            if pm is not None:
                for p in children(tag(pm, "plugins"), "plugin"):
                    pg, pa, pv = text(p, "groupId"), text(p, "artifactId"), text(p, "version")
                    if pg and pa:
                        pom.plugin_mgmt[(pg, pa)] = pv
            for p in children(tag(build, "plugins"), "plugin"):
                pg, pa, pv = text(p, "groupId"), text(p, "artifactId"), text(p, "version")
                if pg and pa:
                    pom.plugins.append({"g": pg, "a": pa, "v": pv})
        return pom


def resolve_props(value, props):
    """Expand ${x} using props (with a few Maven built-ins)."""
    if value is None:
        return None
    out = value
    for _ in range(10):
        m = re.search(r"\$\{([^}]+)\}", out)
        if not m:
            break
        key = m.group(1)
        rep = props.get(key)
        if rep is None:
            if key == "project.version":
                rep = props.get("__project_version__", "")
            else:
                break
        out = out.replace(m.group(0), rep)
    return out


class Resolver:
    def __init__(self, fetcher, root_dm=None, root_props=None):
        self.f = fetcher
        self.resolved = {}      # (g,a) -> (version, depth, scope)
        self.pom_chain = {}     # (g,a,v) -> merged Pom info
        self.import_boms = set()
        # The root project's effective dependencyManagement: Maven applies
        # it to every transitive dependency without an explicit version,
        # and it beats the dependency's own chain depMgmt (nearest wins).
        self.root_dm = root_dm or {}
        self.root_props = root_props or {}

    def merged_pom(self, g, a, v, depth=0, _seen=None):
        """Fetch a POM plus its parent chain; return (props, dep_mgmt, plugin_mgmt).
        Also follows scope=import BOMs in dependencyManagement (Maven does
        this when building the effective model; without it, versions managed
        only by an imported BOM are missing)."""
        if _seen is None:
            _seen = set()
        props, dm, pm = {}, {}, {}
        chain = []
        cur = (g, a, v)
        while cur and depth < 12:
            pom = self.f.fetch_pom(*cur)
            if pom is None:
                break
            chain.append(pom)
            cur = pom.parent
            depth += 1
        # parents first, child overwrites: nearest definition wins (Maven rule).
        # A None version in the child must NOT overwrite the parent's
        # version (e.g. spring-boot-starter-parent lists plugins without a
        # version; spring-boot-dependencies manages them).
        for pom in reversed(chain):
            props.update(pom.props)
            for k, val in pom.dep_mgmt.items():
                if val[0] is not None:
                    dm[k] = val
            for k, val in pom.plugin_mgmt.items():
                if val is not None:
                    pm[k] = val
        # imported BOMs (only those in the merged dep_mgmt)
        for (ig, ia), (iv, iscope) in list(dm.items()):
            if iscope != "import" or iv is None:
                continue
            iv = resolve_props(iv, props)
            if iv is None or (ig, ia, iv) in _seen:
                continue
            _seen.add((ig, ia, iv))
            self.import_boms.add((ig, ia, iv))
            ip, idm, ipm, _ = self.merged_pom(ig, ia, iv, depth=depth + 1, _seen=_seen)
            for k, val in ip.items():
                props.setdefault(k, val)
            for k, (dv2, sc2) in idm.items():
                dv2 = resolve_props(dv2, ip) or dv2
                dm.setdefault(k, (dv2, sc2))
            for k, val in ipm.items():
                pm.setdefault(k, val)
        props["__project_version__"] = v
        if chain:
            props["project.groupId"] = chain[0].g
            props["project.artifactId"] = chain[0].a
            props["project.version"] = chain[0].v
            if len(chain) > 1:
                props["project.parent.version"] = chain[1].v
                props["project.parent.groupId"] = chain[1].g
                props["project.parent.artifactId"] = chain[1].a
        return props, dm, pm, chain

    def resolve(self, roots):
        """roots: list of (g,a,v,scope). BFS, nearest wins, scope propagation."""
        queue = deque()
        for g, a, v, scope in roots:
            queue.append((g, a, v, scope, 0, frozenset()))
        while queue:
            g, a, v, scope, depth, excluded = queue.popleft()
            if (g, a) in excluded:
                continue
            if (g, a) in self.resolved:
                prev_v, prev_d, prev_s = self.resolved[(g, a)]
                if depth >= prev_d:
                    continue
            self.resolved[(g, a)] = (v, depth, scope)
            props, dm, pm, chain = self.merged_pom(g, a, v)
            if not chain:
                print(f"  WARN no POM for {g}:{a}:{v}", file=sys.stderr)
                continue
            pom = chain[0]
            for d in pom.deps:
                if d["optional"]:
                    continue
                dg = resolve_props(d["g"], props) or d["g"]
                da = resolve_props(d["a"], props) or d["a"]
                if (dg, da) in d["exclusions"]:
                    continue
                # Maven rule (confirmed by Maven's own error output, which
                # wants classmate 1.7.0 although hibernate-core declares
                # 1.5.1): the root project's dependencyManagement
                # OVERRIDES the version of a transitive dependency, even
                # when the declaring POM states one explicitly.
                dv = None
                dm_entry = self.root_dm.get((dg, da))
                if dm_entry:
                    dv = resolve_props(dm_entry[0], self.root_props)
                if dv is None:
                    dv = resolve_props(d["v"], props)
                if dv is None:
                    dm_entry = dm.get((dg, da))
                    dv = resolve_props(dm_entry[0], props) if dm_entry else None
                if dv is None:
                    print(f"  WARN no version for {dg}:{da} (from {g}:{a})", file=sys.stderr)
                    continue
                if re.search(r"\[|\]|\(|\)|,", dv or ""):
                    print(f"  WARN version range {dv} for {dg}:{da} -- skipped", file=sys.stderr)
                    continue
                # scope propagation (Maven rules, simplified)
                child_scope = d["scope"]
                if scope == "test" and child_scope in ("compile", "runtime"):
                    child_scope = "test"
                elif scope in ("provided", "runtime") and child_scope == "compile":
                    child_scope = scope
                if child_scope == "provided":
                    continue  # provided deps are not transitive
                if d["scope"] == "test":
                    continue  # test deps of a dependency are never transitive
                queue.append((dg, da, dv, child_scope, depth + 1,
                              excluded | d["exclusions"]))
        return self.resolved


def download_all(fetcher, resolved, jobs=16):
    if isinstance(resolved, set):
        triples = sorted(resolved)
    else:
        triples = sorted((g, a, v) for (g, a), (v, depth, scope) in resolved.items())
    tasks = []
    for (g, a, v) in triples:
        tasks.append((g, a, v, "pom"))
        tasks.append((g, a, v, "jar"))

    def one(task):
        g, a, v, ext = task
        dest = fetcher.path(g, a, v, ext)
        url = f"{BASE}{'/'.join(g.split('.'))}/{a}/{v}/{a}-{v}.{ext}"
        ok = fetcher.curl(url, dest)
        return (task, ok)

    missing = []
    with cf.ThreadPoolExecutor(max_workers=jobs) as ex:
        for task, ok in ex.map(one, tasks):
            if not ok:
                missing.append(task)
    # _remote.repositories markers (Maven offline needs the repo id)
    for (g, a, v) in triples:
        d = os.path.dirname(fetcher.path(g, a, v, "pom"))
        marker = os.path.join(d, "_remote.repositories")
        lines = []
        for ext in ("pom", "jar"):
            fn = f"{a}-{v}.{ext}"
            if os.path.exists(os.path.join(d, fn)):
                lines.append(f"{fn}>central=")
        if lines:
            with open(marker, "w") as fh:
                fh.write("#NOTE: downloaded via curl (see ops/fetch-deps.py)\n" + "\n".join(lines) + "\n")
    return missing


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default="/home/hatch/.m2/repository")
    ap.add_argument("--pom", default="/home/hatch/workspace/unilink/backend/pom.xml")
    args = ap.parse_args()
    fetcher = Fetcher(args.repo)
    root = ET.parse(args.pom).getroot()
    proj_g = text(root, "groupId")
    parent_el = tag(root, "parent")
    parent = (text(parent_el, "groupId"), text(parent_el, "artifactId"),
              text(parent_el, "version")) if parent_el is not None else None
    # project properties
    props_el = tag(root, "properties")
    proj_props = {c.tag.replace(NS, ""): c.text.strip() for c in props_el if c.text} if props_el is not None else {}
    # parent chain props/dep_mgmt for version lookup of direct deps.
    # The project's own dependencyManagement (none in this pom, but handle
    # it generally) is added on top, then this whole map is the ROOT_DM
    # passed to the resolver (see Resolver docstring).
    _r0 = Resolver(fetcher)
    props, dm, pm, chain = _r0.merged_pom(*parent) if parent else ({}, {}, {}, [])
    props.update(proj_props)
    # project pom's own dependencyManagement
    _dm_el = tag(root, "dependencyManagement")
    if _dm_el is not None:
        for _d in children(tag(_dm_el, "dependencies"), "dependency"):
            _dg, _da, _dv = text(_d, "groupId"), text(_d, "artifactId"), text(_d, "version")
            if _dg and _da and _dv:
                dm[(_dg, _da)] = (_dv, text(_d, "scope"))
    root_dm, root_props = dm, props
    import_boms0 = _r0.import_boms
    roots = []
    deps_el = tag(root, "dependencies")
    for d in children(deps_el, "dependency") if deps_el is not None else []:
        dg, da, dv = text(d, "groupId"), text(d, "artifactId"), text(d, "version")
        scope = text(d, "scope") or "compile"
        if dv is None:
            dm_entry = dm.get((dg, da))
            dv = resolve_props(dm_entry[0], props) if dm_entry else None
        dv = resolve_props(dv, props)
        if dv is None:
            print(f"WARN direct dep {dg}:{da} has no version", file=sys.stderr)
            continue
        roots.append((dg, da, dv, scope))
    # build plugins as roots (their own compile deps are needed offline)
    plugins = []
    build = tag(root, "build")
    if build is not None:
        for p in children(tag(build, "plugins"), "plugin"):
            pg, pa, pv = text(p, "groupId"), text(p, "artifactId"), text(p, "version")
            pv = resolve_props(pv, props) or pm.get((pg, pa))
            if pg and pa and pv:
                plugins.append((pg, pa, pv))
    # lifecycle plugins Maven needs even when not declared
    for pg, pa in [("org.apache.maven.plugins", "maven-compiler-plugin"),
                   ("org.apache.maven.plugins", "maven-resources-plugin"),
                   ("org.apache.maven.plugins", "maven-jar-plugin")]:
        pv = pm.get((pg, pa))
        if pv and (pg, pa, pv) not in plugins:
            plugins.append((pg, pa, resolve_props(pv, props)))
    print(f"direct deps: {len(roots)}, plugins: {len(plugins)}")
    resolver = Resolver(fetcher, root_dm=root_dm, root_props=root_props)
    resolver.import_boms |= import_boms0
    resolved = resolver.resolve(roots)
    # plugin realms: separate resolution per plugin (own chain dm, no
    # root_dm override, no sharing with the project graph). Collect every
    # (g,a,v) triple -- different versions of one artifact may be needed
    # in different realms.
    download_set = {(g, a, v) for (g, a), (v, d, s) in resolved.items()}
    for (pg, pa, pv) in plugins:
        pr = Resolver(fetcher)
        pres = pr.resolve([(pg, pa, pv, "compile")])
        resolver.import_boms |= pr.import_boms
        for k, val in pres.items():
            download_set.add((k[0], k[1], val[0]))
    print(f"resolved artifacts: {len(resolved)}, import BOMs: {len(resolver.import_boms)}")
    resolved = download_set
    for (g, a, v) in sorted(resolver.import_boms):
        dest = fetcher.path(g, a, v, "pom")
        url = f"{BASE}{'/'.join(g.split('.'))}/{a}/{v}/{a}-{v}.pom"
        if fetcher.curl(url, dest):
            d = os.path.dirname(dest)
            with open(os.path.join(d, "_remote.repositories"), "w") as fh:
                fh.write(f"{a}-{v}.pom>central=\n")
    missing = download_all(fetcher, resolved)
    pom_missing = [t for t in missing if t[3] == "pom"]
    jar_missing = [t for t in missing if t[3] == "jar"]
    print(f"downloaded; missing poms: {len(pom_missing)}, missing jars: {len(jar_missing)}")
    for t in pom_missing + jar_missing:
        print("  MISSING", t)
    return 0 if not pom_missing else 1


if __name__ == "__main__":
    sys.exit(main())
