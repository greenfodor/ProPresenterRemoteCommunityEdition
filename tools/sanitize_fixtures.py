#!/usr/bin/env python3
"""Generates sanitised test fixtures from ProPresenter HTTP API captures.

Usage: sanitize_fixtures.py <captures dir> <out dir>

<captures dir> holds the raw captures plus sanitize-map.json (curated names for the tested
entities). Every other name is replaced by a generated placeholder, slide text and notes by
"<group> · <n>", file paths by C:\\PP\\<name>.pro and private IPv4 addresses by 192.0.2.14.
The script ends with a leak check and exits 1 if any original name, lyric line, user path or
private IP is still present in the output.
"""
import json
import re
import sys
from pathlib import Path

PLAYLIST_TREE = "t/playlists-now.json"
PLAYLISTS = [
    "watch/215006-playlist.json",
    "t/pl-065f53c3-e299-4e07-8ac2-258ca76b7188.json",
]
PRESENTATIONS = [
    "watch/215149-pres-08672906.json",
    "t/pres-prunc.json",
    "watch/214950-pres-1d6c5bd9.json",
]
VERSION = "t/version.json"
STREAMS = [
    "status-updates",
    "su-long",
    "session1-status-updates",
    "session2-status-updates",
]

FRAME_SEPARATOR = b"\r\n\r\n"
PLACEHOLDER_IP = "192.0.2.14"
GROUP_WHITELIST = re.compile(r"^(Intro|Verse|Pre-?Chorus|Chorus|Bridge|Tag|Ending|Loop)( ?\d+)?$", re.IGNORECASE)
PRIVATE_IP = re.compile(
    r"\b(?:192\.168(?:\.\d{1,3}){2}|10(?:\.\d{1,3}){3}|172\.(?:1[6-9]|2\d|3[01])(?:\.\d{1,3}){2})\b"
)
USER_DIR = "Users"
USER_PATH = re.compile(r"[A-Za-z]:\\+" + USER_DIR + r"\\+[^\"]*")
CHUNK_LINE = re.compile(r"^# \+(?P<time>[\d.]+)s chunk (?P<n>\d+) \((?P<size>\d+) B\) tail=.*$")
TOTAL_LINE = re.compile(r"^# total=\d+ B in (?P<rest>.*)$")
PLACEHOLDER_WORDS = {
    "presentation", "playlist", "folder", "arrangement", "group", "header", "media", "label",
    "text", "notes", "item", "host", "song", "full", "chorus", "only", "short", "bridge",
    "service", "test", "+", "·",
}
MIN_NAME_SUBSTRING = 5
MIN_LYRIC_LINE = 8


class Sanitizer:
    def __init__(self, curated):
        self.curated_presentations = curated["presentations"]
        self.curated_folders = curated["folders"]
        self.curated_playlists = curated["playlists"]
        self.counters = {}
        self.generated_names = {}
        self.presentation_names = {}
        self.playlist_names = {}
        self.item_names = {}
        self.texts = {}
        self.leaked_names = set()
        self.lyric_lines = set()

    def generate(self, kind, key):
        if (kind, key) not in self.generated_names:
            self.counters[kind] = self.counters.get(kind, 0) + 1
            self.generated_names[(kind, key)] = f"{kind} {self.counters[kind]:02d}"
        return self.generated_names[(kind, key)]

    def replaced(self, original, replacement):
        if original and original != replacement:
            self.leaked_names.add(original)
        return replacement

    def presentation_name(self, uuid, original):
        if uuid not in self.presentation_names:
            curated = self.curated_presentations.get(uuid)
            self.presentation_names[uuid] = curated["name"] if curated else self.generate("Presentation", uuid)
        return self.replaced(original, self.presentation_names[uuid])

    def arrangement_name(self, presentation_uuid, original):
        if original == "":
            return ""
        curated = self.curated_presentations.get(presentation_uuid, {}).get("arrangements", {})
        replacement = curated[original] if original in curated else self.generate("Arrangement", original)
        return self.replaced(original, replacement)

    def group_name(self, original):
        if GROUP_WHITELIST.match(original):
            return original
        return self.replaced(original, self.generate("Group", original))

    def label(self, original):
        if original == "":
            return ""
        return self.replaced(original, self.generate("Label", original))

    def slide_text(self, original, placeholder):
        if original.strip() == "":
            return original
        for line in re.split(r"\r\n|\n|\r", original):
            if len(line.strip()) >= MIN_LYRIC_LINE:
                self.lyric_lines.add(line.strip())
        return self.texts.setdefault(text_key(original), placeholder)

    def stream_text(self, original):
        if original.strip() == "":
            return original
        if text_key(original) not in self.texts:
            return self.slide_text(original, self.generate("Text", text_key(original)))
        return self.texts[text_key(original)]

    def folder_or_playlist(self, node):
        node_id = node["id"]
        original = node_id["name"]
        if node.get("field_type") == "group":
            replacement = self.curated_folders.get(original) or self.generate("Folder", node_id["uuid"])
        else:
            replacement = self.curated_playlists.get(original) or self.generate("Playlist", node_id["uuid"])
            self.playlist_names[node_id["uuid"]] = replacement
        node_id["name"] = self.replaced(original, replacement)
        for child in node.get("children") or []:
            self.folder_or_playlist(child)

    def playlist_name(self, playlist_id):
        uuid = playlist_id["uuid"]
        original = playlist_id["name"]
        if uuid not in self.playlist_names:
            self.playlist_names[uuid] = self.curated_playlists.get(original) or self.generate("Playlist", uuid)
        playlist_id["name"] = self.replaced(original, self.playlist_names[uuid])

    def playlist_item(self, item):
        item_id = item["id"]
        original = item_id["name"]
        info = item.get("presentation_info")
        if info:
            presentation_uuid = info["presentation_uuid"]
            replacement = self.presentation_name(presentation_uuid, original)
            info["arrangement_name"] = self.arrangement_name(presentation_uuid, info["arrangement_name"])
        elif item["type"] == "header":
            replacement = self.generate("Header", item_id["uuid"])
        elif item["type"] == "media":
            replacement = self.generate("Media", item_id["uuid"])
        else:
            replacement = self.generate("Item", item_id["uuid"])
        self.item_names[item_id["uuid"]] = replacement
        item_id["name"] = self.replaced(original, replacement)

    def playlist(self, playlist):
        self.playlist_name(playlist["id"])
        for item in playlist.get("items") or []:
            self.playlist_item(item)

    def presentation(self, presentation):
        presentation_id = presentation["id"]
        uuid = presentation_id["uuid"]
        name = self.presentation_name(uuid, presentation_id["name"])
        presentation_id["name"] = name
        for group in presentation["groups"]:
            group_name = self.group_name(group["name"])
            group["name"] = group_name
            for number, slide in enumerate(group["slides"], start=1):
                slide["text"] = self.slide_text(slide["text"], f"{group_name} · {number}")
                slide["notes"] = self.slide_text(slide["notes"], f"{group_name} · {number} notes")
                slide["label"] = self.label(slide["label"])
        for arrangement in presentation["arrangements"]:
            arrangement["id"]["name"] = self.arrangement_name(uuid, arrangement["id"]["name"])
        if presentation.get("presentation_path"):
            presentation["presentation_path"] = self.replaced(presentation["presentation_path"], f"C:\\PP\\{name}.pro")

    def frame(self, frame):
        url = frame["url"]
        data = frame["data"]
        if url == "status/slide":
            for slide in (data.get("current"), data.get("next")):
                if slide:
                    slide["text"] = self.stream_text(slide["text"])
                    slide["notes"] = self.stream_text(slide["notes"])
        elif url == "presentation/active":
            if data.get("presentation"):
                self.presentation(data["presentation"])
        elif url == "presentation/slide_index":
            index = data.get("presentation_index")
            if index:
                presentation_id = index["presentation_id"]
                presentation_id["name"] = self.presentation_name(presentation_id["uuid"], presentation_id["name"])
        elif url == "playlist/active":
            for section in data.values():
                if not section:
                    continue
                if section.get("playlist"):
                    self.playlist_name(section["playlist"])
                if section.get("playlist_item"):
                    self.playlist_item(section["playlist_item"])
                if section.get("item"):
                    item = section["item"]
                    item["name"] = self.replaced(
                        item["name"], self.item_names.get(item["uuid"]) or self.generate("Item", item["uuid"])
                    )
        elif url != "timer/system_time":
            raise ValueError(f"no sanitising rule for stream url {url}")
        return frame


def text_key(text):
    return " ".join(text.split())


def scrub(text):
    return USER_PATH.sub("C:\\\\PP", PRIVATE_IP.sub(PLACEHOLDER_IP, text))


def dump(value, pretty):
    if pretty:
        return scrub(json.dumps(value, ensure_ascii=False, indent=2)) + "\n"
    return scrub(json.dumps(value, ensure_ascii=False, separators=(",", ":")))


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(dump(value, pretty=True), encoding="utf-8")


def sanitize_stream(sanitizer, source_dir, name, out_dir):
    raw = (source_dir / f"{name}.raw").read_bytes()
    meta_lines = (source_dir / f"{name}.meta").read_text(encoding="utf-8").splitlines()
    frames = [part for part in raw.split(FRAME_SEPARATOR) if part.strip()]
    sanitized = [
        dump(sanitizer.frame(json.loads(part)), pretty=False).encode("utf-8") + FRAME_SEPARATOR for part in frames
    ]
    original_sizes = [len(part) + len(FRAME_SEPARATOR) for part in frames]

    out_raw = b""
    out_meta = []
    frame_index = 0
    for line in meta_lines:
        chunk = CHUNK_LINE.match(line)
        total = TOTAL_LINE.match(line)
        if chunk:
            remaining = int(chunk["size"])
            chunk_bytes = b""
            while remaining > 0:
                remaining -= original_sizes[frame_index]
                chunk_bytes += sanitized[frame_index]
                frame_index += 1
            if remaining != 0:
                raise ValueError(f"{name}: chunk {chunk['n']} does not end on a frame boundary")
            out_raw += chunk_bytes
            out_meta.append(
                f"# +{chunk['time']}s chunk {chunk['n']} ({len(chunk_bytes)} B) tail={chunk_bytes[-6:]!r}"
            )
        elif total:
            out_meta.append(f"# total={len(out_raw)} B in {total['rest']}")
        else:
            out_meta.append(scrub(line))
    if frame_index != len(frames):
        raise ValueError(f"{name}: {len(frames) - frame_index} frames are not covered by the meta chunks")

    streams_dir = out_dir / "streams"
    streams_dir.mkdir(parents=True, exist_ok=True)
    (streams_dir / f"{name}.raw").write_bytes(out_raw)
    (streams_dir / f"{name}.meta").write_text("\n".join(out_meta) + "\n", encoding="utf-8")


def json_strings(value):
    if isinstance(value, dict):
        for item in value.values():
            yield from json_strings(item)
    elif isinstance(value, list):
        for item in value:
            yield from json_strings(item)
    elif isinstance(value, str):
        yield value


def output_strings(path):
    content = path.read_bytes()
    if path.suffix == ".raw":
        for part in content.split(FRAME_SEPARATOR):
            if part.strip():
                yield from json_strings(json.loads(part))
    elif path.suffix == ".json":
        yield from json_strings(json.loads(content))


def is_placeholder_vocabulary(name):
    return all(word.lower() in PLACEHOLDER_WORDS or word.isdigit() for word in name.split())


def leak_check(sanitizer, out_dir):
    leaks = []
    substring_names = [
        name for name in sanitizer.leaked_names
        if len(name) >= MIN_NAME_SUBSTRING and not is_placeholder_vocabulary(name)
    ]
    files = sorted(path for path in out_dir.rglob("*") if path.is_file())
    for path in files:
        content = path.read_text(encoding="utf-8")
        lowered = content.lower()
        for value in output_strings(path):
            if value in sanitizer.leaked_names:
                leaks.append(f"{path}: original name as a value")
        for name in substring_names:
            if name.lower() in lowered:
                leaks.append(f"{path}: original name inside the content")
        for line in sanitizer.lyric_lines:
            if line.lower() in lowered:
                leaks.append(f"{path}: original slide text line")
        if USER_DIR.lower() + "\\" in lowered:
            leaks.append(f"{path}: user path")
        if PRIVATE_IP.search(content):
            leaks.append(f"{path}: private IP address")
    if leaks:
        for leak in sorted(set(leaks)):
            print(f"LEAK {leak}", file=sys.stderr)
        return False
    print(
        f"Leak check passed: {len(files)} files, {len(sanitizer.leaked_names)} original names "
        f"and {len(sanitizer.lyric_lines)} slide text lines checked"
    )
    return True


def main():
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    source_dir = Path(sys.argv[1])
    out_dir = Path(sys.argv[2])
    curated = json.loads((source_dir / "sanitize-map.json").read_text(encoding="utf-8"))
    sanitizer = Sanitizer(curated)

    def load(relative):
        return json.loads((source_dir / relative).read_text(encoding="utf-8"))

    tree = load(PLAYLIST_TREE)
    for node in tree:
        sanitizer.folder_or_playlist(node)
    write_json(out_dir / "playlists.json", tree)

    for relative in PLAYLISTS:
        playlist = load(relative)
        sanitizer.playlist(playlist)
        write_json(out_dir / f"playlist-{playlist['id']['uuid'][:8]}.json", playlist)

    for relative in PRESENTATIONS:
        wrapper = load(relative)
        sanitizer.presentation(wrapper["presentation"])
        write_json(out_dir / f"presentation-{wrapper['presentation']['id']['uuid'][:8]}.json", wrapper)

    version = load(VERSION)
    version["name"] = sanitizer.replaced(version["name"], sanitizer.generate("Host", version["name"]))
    write_json(out_dir / "version.json", version)

    for name in STREAMS:
        sanitize_stream(sanitizer, source_dir / "streams", name, out_dir)

    return 0 if leak_check(sanitizer, out_dir) else 1


if __name__ == "__main__":
    sys.exit(main())
