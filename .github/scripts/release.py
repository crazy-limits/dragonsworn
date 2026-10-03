#!/usr/bin/env python3
"""
Works out the next version from Conventional Commits (https://www.conventionalcommits.org).

  release.py next [--bump auto|patch|minor|major] [--channel release|beta|alpha] [--write]
      Prints the next version, and with --write stores it as mod.version and writes release notes to
      CHANGELOG.md. `feat` bumps the minor version, `fix` and `perf` bump the patch version, and a `!` after
      the type or a `BREAKING CHANGE:` footer bumps the major version. Other types (chore, docs, ci, ...)
      don't make a release on their own.

      The bump is counted from the last stable release. The alpha and beta channels make a semver
      pre-release of that next version, numbered after the ones already tagged: 1.10.0-alpha.1,
      1.10.0-alpha.2, 1.10.0-beta.1, then 1.10.0 on the release channel.

  release.py lint <revision range>
      Fails if a commit in the range doesn't follow Conventional Commits.

Releases are tagged with the bare version (`1.10.0`, `1.10.0-alpha.1`), like the tags before this script.
Tags with build metadata from older releases (`1.9.2+1.21.4`) count as their base version.
"""

import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROPERTIES = ROOT / "stonecutter.properties.toml"
CHANGELOG = ROOT / "CHANGELOG.md"

TYPES = ("feat", "fix", "perf", "refactor", "revert", "docs", "style", "test", "build", "ci", "chore")
HEADER = re.compile(r"^(?P<type>[a-z]+)(?:\((?P<scope>[^()\s]+)\))?(?P<breaking>!)?: (?P<subject>\S.*)$")
BREAKING_FOOTER = re.compile(r"^BREAKING[ -]CHANGE: (?P<text>.+)$", re.MULTILINE)
VERSION = re.compile(r"^(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta)\.(\d+))?(?:\+.*)?$")
MOD_VERSION = re.compile(r'^(mod\.version\s*=\s*")([^"]*)(")', re.MULTILINE)

BUMPS = ("patch", "minor", "major")
CHANNELS = ("release", "beta", "alpha")
SECTIONS = (("breaking", "Breaking changes"), ("feat", "Features"), ("fix", "Fixes"), ("perf", "Performance"))


class Version:
    def __init__(self, core, channel=None, number=0):
        self.core = core  # (major, minor, patch)
        self.channel = channel  # None for a stable release, else "alpha" or "beta"
        self.number = number

    @classmethod
    def parse(cls, text):
        match = VERSION.match(text)
        if not match:
            return None
        core = tuple(int(part) for part in match.groups()[:3])
        return cls(core, match[4], int(match[5])) if match[4] else cls(core)

    @property
    def stable(self):
        return self.channel is None

    def key(self):
        # A pre-release sorts before its stable release, and alpha before beta
        return self.core, self.stable, self.channel or "", self.number

    def __lt__(self, other):
        return self.key() < other.key()

    def __eq__(self, other):
        return self.key() == other.key()

    def __hash__(self):
        return hash(self.key())

    def __str__(self):
        text = ".".join(str(part) for part in self.core)
        return f"{text}-{self.channel}.{self.number}" if self.channel else text


def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, check=True, capture_output=True, text=True).stdout


def bump(core, level):
    major, minor, patch = core
    if level == "major":
        return major + 1, 0, 0
    if level == "minor":
        return major, minor + 1, 0
    return major, minor, patch + 1


def released():
    """Every released version reachable from HEAD, with the tags naming it."""
    tags = {}
    for tag in git("tag", "--merged", "HEAD").split():
        version = Version.parse(tag)
        if version:
            tags.setdefault(version, []).append(tag)
    return tags


def commits(*revisions):
    """(hash, subject, body) of each commit, oldest first, merge commits left out."""
    log = git("log", "--no-merges", "--reverse", "--format=%H%x1f%s%x1f%b%x1e", *revisions)
    for record in log.split("\x1e"):
        record = record.strip("\n")
        if record:
            sha, subject, body = record.split("\x1f")
            yield sha, subject, body


def changes_since(tags):
    revisions = ["HEAD", *(f"^{tag}" for tag in tags)]
    return [change for change in (parse(s, b) for _, s, b in commits(*revisions)) if change]


def parse(subject, body):
    match = HEADER.match(subject)
    if not match or match["type"] not in TYPES:
        return None
    footer = BREAKING_FOOTER.search(body)
    return {
        "type": match["type"],
        "scope": match["scope"],
        "subject": match["subject"],
        "breaking": bool(match["breaking"] or footer),
        "breaking_note": footer["text"] if footer else None,
    }


def bump_level(changes):
    if any(c["breaking"] for c in changes):
        return "major"
    if any(c["type"] == "feat" for c in changes):
        return "minor"
    if any(c["type"] in ("fix", "perf") for c in changes):
        return "patch"
    return None


def read_mod_version():
    match = MOD_VERSION.search(PROPERTIES.read_text())
    version = match and Version.parse(match[2])
    if not version:
        sys.exit(f"no valid mod.version in {PROPERTIES.name}")
    return version


def write_mod_version(version):
    text = PROPERTIES.read_text()
    PROPERTIES.write_text(MOD_VERSION.sub(lambda m: m[1] + str(version) + m[3], text, count=1))


def release_notes(changes):
    lines = []
    for key, title in SECTIONS:
        if key == "breaking":
            entries = [c["breaking_note"] or c["subject"] for c in changes if c["breaking"]]
        else:
            entries = [c["subject"] for c in changes if c["type"] == key and not c["breaking"]]
        entries = [f"- {entry[0].upper()}{entry[1:]}" for entry in entries]
        if entries:
            lines += [f"### {title}", *entries, ""]
    return "\n".join(lines)


def next_version(args):
    tags = released()
    stable = [version for version in tags if version.stable]
    last_stable = max(stable) if stable else Version((0, 0, 0))

    # The bump level counts everything since the last stable release, so alpha.2 stays on the same version
    level = args.bump if args.bump != "auto" else bump_level(changes_since(tags.get(last_stable, [])))
    core = bump(last_stable.core, level) if level else None
    # mod.version may already have been raised by hand ahead of the release; never go below it
    stored = read_mod_version().core
    if stored > last_stable.core and (core is None or stored > core):
        core = stored
    if core is None:
        sys.exit(f"nothing to release since {last_stable}: no feat, fix, perf or breaking commits")

    if args.channel == "release":
        version = Version(core)
    else:
        channel = args.channel
        taken = [v.number for v in tags if v.core == core and v.channel == channel]
        version = Version(core, channel, max(taken, default=0) + 1)
    if version in tags:
        sys.exit(f"{version} is already released")

    # A pre-release lists what changed since the previous release on any channel, a stable release
    # everything since the last stable one
    previous = max((v for v in tags if v < version and (v.stable or not version.stable)), default=None)
    notes = release_notes(changes_since(tags[previous] if previous else []))
    if not notes:
        # Nothing conventional to list: keep the notes someone already wrote in CHANGELOG.md
        notes = CHANGELOG.read_text().strip() + "\n"
    if args.write:
        write_mod_version(version)
        CHANGELOG.write_text(notes)
    else:
        print(notes, file=sys.stderr)
    print(version)


def lint(args):
    bad = [(sha, subject) for sha, subject, body in commits(args.range) if not parse(subject, body)]
    for sha, subject in bad:
        print(f"::error::{sha[:8]} is not a conventional commit: {subject}")
    if bad:
        print(f"\nCommit subjects must look like `type(scope): subject`, type one of {', '.join(TYPES)}.")
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)

    next_parser = commands.add_parser("next", help="print the next version")
    next_parser.add_argument("--bump", choices=("auto", *BUMPS), default="auto")
    next_parser.add_argument("--channel", choices=CHANNELS, default="release")
    next_parser.add_argument("--write", action="store_true", help="update mod.version and CHANGELOG.md")
    next_parser.set_defaults(run=next_version)

    lint_parser = commands.add_parser("lint", help="check commit messages")
    lint_parser.add_argument("range", help="revision range, like origin/master..HEAD")
    lint_parser.set_defaults(run=lint)

    args = parser.parse_args()
    args.run(args)


if __name__ == "__main__":
    main()
