#!/usr/bin/env python3
"""Generates sanitised test fixtures from ProPresenter HTTP API captures.

Usage: sanitize_fixtures.py <captures dir> <out dir>

<captures dir> holds the raw captures plus sanitize-map.json (curated names for the tested
entities). Every other name is replaced by a generated placeholder, slide text and notes by
"<group> · <n>", file paths by C:\\PP\\<name>.pro and private IPv4 addresses by 192.0.2.14.
The script ends with a leak check and exits 1 if any original name, lyric line, user path or
private IP is still present in the output, or if any image or binary file (*.jpg, *.jpeg, *.png,
*.bin) is under core/data/src/test/resources/.
"""
import json
import re
import sys
from pathlib import Path

PLAYLIST_TREE = "t/playlists-now.json"
PLAYLISTS = [
    "stage4/pprtest2.json",
    "t/pl-065f53c3-e299-4e07-8ac2-258ca76b7188.json",
]
PRESENTATIONS = [
    "watch/215149-pres-08672906.json",
    "t/pres-prunc.json",
    "../stage4-device/pres-1d6c5bd9.json",
    "stage4/pres-disabled.json",
]
VERSION = "t/version.json"
CLEAR_GROUPS = "../stage5/v1_clear_groups.json"
LIBRARIES = "../stage6/libraries.json"
LIBRARY = "../stage6/library-0.json"
LIBRARY_INDEX = 0
TIMERS = "../stage8/_v1_timers.json"
TIMERS_CURRENT = "../stage8/_v1_timers_current.json"
TIMER_TYPES = {"countdown", "count_down_to_time", "elapsed"}
TIMER_STATES = {"stopped", "running", "complete", "overrunning", "overran", "overrun"}
TIMER_PERIODS = {"am", "pm", "is_24_hour"}
TIMER_TIME = re.compile(r"^-?\d{2}:\d{2}:\d{2}(\.\d+)?$")
MACRO_IMAGE_TYPES = {"Default"}
MACRO_ACTION_TYPES = {"stage_layout", "timer", "audience_look", "clear"}
CLEAR_GROUPS_OUT = "clear-groups.json"
CLEAR_GROUP_LAYERS = {
    "music", "audio_effects", "messages", "props", "announcements", "presentation", "presentation_media",
    "video_input",
}
CLEAR_GROUP_ICONS = {"All"}
DEFAULT_CLEAR_GROUP_NAMES = {"Clear All"}
STREAMS = [
    "streams/status-updates",
    "streams/su-long",
    "streams/session1-status-updates",
    "streams/session2-status-updates",
    "../stage5-device/streams/stage5-status-updates",
    "../stage6/streams/stage6-probe",
    "../stage7/streams/stage7-probe",
    "../stage8/streams/stage8-timers",
]

FRAME_SEPARATOR = b"\r\n\r\n"
PLACEHOLDER_IP = "192.0.2.14"
GROUP_WHITELIST = re.compile(r"^(Intro|Verse|Pre-?Chorus|Chorus|Bridge|Tag|Ending|Loop)( ?\d+)?$", re.IGNORECASE)
IPV4 = re.compile(r"(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?![\d.])")
USER_DIRS = ("Users", "home")
USER_PATH = re.compile(r"(?:[A-Za-z]:[\\/]+|/)(?:" + "|".join(USER_DIRS) + r")[\\/].*", re.IGNORECASE)
UUID = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
VERBATIM_ALLOWED = {
    "presentation", "header", "media", "playlist", "group", "standard", "win", "v1", "all",
    "ProPresenter 21.4.2", "10.0.26200",
    "status/slide", "presentation/active", "presentation/slide_index", "playlist/active", "timer/system_time",
    "status/layers", "timers", "timers/current", "macro_collections",
}
CHUNK_LINE = re.compile(r"^# \+(?P<time>[\d.]+)s chunk (?P<n>\d+) \((?P<size>\d+) B\) tail=.*$")
TOTAL_LINE = re.compile(r"^# total=\d+ B in (?P<rest>.*)$")
PLACEHOLDER_WORDS = {
    "presentation", "playlist", "folder", "arrangement", "group", "header", "media", "label",
    "text", "notes", "item", "host", "song", "full", "chorus", "only", "short", "bridge",
    "service", "test", "total", "start", "+", "·",
}
TEST_RESOURCES = Path(__file__).resolve().parent.parent / "core" / "data" / "src" / "test" / "resources"
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".bin"}
MIN_NAME_SUBSTRING = 4
MIN_LYRIC_LINE = 5
PRESENTATION_NUMBER_WIDTH = 3


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
        self.input_strings = set()
        self.kept_names = {
            name
            for presentation in self.curated_presentations.values()
            for original, name in presentation["arrangements"].items()
            if original == name
        }

    def remember(self, value):
        self.input_strings.update(json_strings(value))
        return value

    def generate(self, kind, key):
        if (kind, key) not in self.generated_names:
            self.counters[kind] = self.counters.get(kind, 0) + 1
            width = PRESENTATION_NUMBER_WIDTH if kind == "Presentation" else 2
            self.generated_names[(kind, key)] = f"{kind} {self.counters[kind]:0{width}d}"
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

    def number_library_entries(self, entries):
        for position, entry in enumerate(entries, start=1):
            if entry["uuid"] not in self.curated_presentations:
                self.presentation_names[entry["uuid"]] = f"Presentation {position:0{PRESENTATION_NUMBER_WIDTH}d}"
        self.counters["Presentation"] = len(entries)

    def library(self, library):
        library["name"] = self.replaced(library["name"], self.generate("Library", library["uuid"]))

    def library_entry(self, entry):
        entry["name"] = self.presentation_name(entry["uuid"], entry["name"])

    def clear_group(self, group):
        if group["id"]["name"] not in DEFAULT_CLEAR_GROUP_NAMES:
            group["id"]["name"] = self.replaced(group["id"]["name"], self.generate("Clear Group", group["id"]["uuid"]))
        unknown = [layer for layer in group["layers"] if layer not in CLEAR_GROUP_LAYERS]
        if unknown or group["icon"] not in CLEAR_GROUP_ICONS:
            raise ValueError(f"no sanitising rule for clear group layers {unknown} or icon {group['icon']!r}")

    def timer_id(self, timer_id):
        timer_id["name"] = self.replaced(timer_id["name"], self.generate("Timer", timer_id["uuid"]))

    def timer(self, timer):
        self.timer_id(timer["id"])
        types = [key for key in timer if key not in ("id", "allows_overrun")]
        if len(types) != 1 or types[0] not in TIMER_TYPES:
            raise ValueError(f"no sanitising rule for timer type {types}")
        period = timer[types[0]].get("period")
        if period is not None and period not in TIMER_PERIODS:
            raise ValueError(f"no sanitising rule for timer period {period!r}")

    def timer_reading(self, reading):
        self.timer_id(reading["id"])
        if reading["state"] not in TIMER_STATES or not TIMER_TIME.match(reading["time"]):
            raise ValueError(f"no sanitising rule for timer state {reading['state']!r} or time {reading['time']!r}")

    def macro_collections(self, data):
        for collection in data["collections"]:
            collection_id = collection["id"]
            collection_id["name"] = self.replaced(
                collection_id["name"], self.generate("Collection", collection_id["uuid"])
            )
            for macro in collection["macros"]:
                macro["id"]["name"] = self.replaced(macro["id"]["name"], self.generate("Macro", macro["id"]["uuid"]))
                actions = {action["type"] for action in macro["actions"]}
                if macro["image_type"] not in MACRO_IMAGE_TYPES or not actions <= MACRO_ACTION_TYPES:
                    raise ValueError(
                        f"no sanitising rule for macro image {macro['image_type']!r} or actions {actions}"
                    )

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
        elif url == "status/layers":
            if not all(isinstance(value, bool) for value in data.values()):
                raise ValueError("status/layers frame with a non-boolean value")
        elif url == "timers":
            for timer in data:
                self.timer(timer)
        elif url == "timers/current":
            for reading in data:
                self.timer_reading(reading)
        elif url == "macro_collections":
            self.macro_collections(data)
        elif url != "timer/system_time":
            raise ValueError(f"no sanitising rule for stream url {url}")
        return frame


def text_key(text):
    return " ".join(text.split())


def scrub(text):
    without_ips = IPV4.sub(lambda match: match[0] if is_documentation_ip(match[0]) else PLACEHOLDER_IP, text)
    return USER_PATH.sub(lambda match: "/PP" if match[0].startswith("/") else "C:\\PP", without_ips)


def is_documentation_ip(address):
    return address.startswith("192.0.2.")


def scrub_value(value):
    if isinstance(value, dict):
        return {key: scrub_value(item) for key, item in value.items()}
    if isinstance(value, list):
        return [scrub_value(item) for item in value]
    if isinstance(value, str):
        return scrub(value)
    return value


def dump(value, pretty):
    if pretty:
        return json.dumps(scrub_value(value), ensure_ascii=False, indent=2) + "\n"
    return json.dumps(scrub_value(value), ensure_ascii=False, separators=(",", ":"))


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(dump(value, pretty=True), encoding="utf-8")


def sanitize_stream(sanitizer, source, out_dir):
    name = source.name
    raw = source.with_name(f"{name}.raw").read_bytes()
    meta_lines = source.with_name(f"{name}.meta").read_text(encoding="utf-8").splitlines()
    frames = [part for part in raw.split(FRAME_SEPARATOR) if part.strip()]
    sanitized = [
        dump(sanitizer.frame(sanitizer.remember(json.loads(part))), pretty=False).encode("utf-8") + FRAME_SEPARATOR
        for part in frames
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
        elif line.startswith("{"):
            continue
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


def is_allowed_verbatim(sanitizer, value, path):
    return (
        path.name == CLEAR_GROUPS_OUT and value in CLEAR_GROUP_LAYERS | CLEAR_GROUP_ICONS | DEFAULT_CLEAR_GROUP_NAMES
    ) or (
        value.strip() == ""
        or UUID.match(value) is not None
        or GROUP_WHITELIST.match(value) is not None
        or value in sanitizer.kept_names
        or value in VERBATIM_ALLOWED
        or value in TIMER_STATES | TIMER_PERIODS | MACRO_IMAGE_TYPES | MACRO_ACTION_TYPES
        or TIMER_TIME.match(value) is not None
    )


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
            elif value in sanitizer.input_strings and not is_allowed_verbatim(sanitizer, value, path):
                leaks.append(f"{path}: input value copied unchanged: {value!r}")
        for name in substring_names:
            if name.lower() in lowered:
                leaks.append(f"{path}: original name inside the content")
        for line in sanitizer.lyric_lines:
            if line.lower() in lowered:
                leaks.append(f"{path}: original slide text line")
        if USER_PATH.search(content):
            leaks.append(f"{path}: user path")
        if any(not is_documentation_ip(address) for address in IPV4.findall(content)):
            leaks.append(f"{path}: IP address")
    for path in sorted(TEST_RESOURCES.rglob("*")):
        if path.is_file() and path.suffix.lower() in IMAGE_SUFFIXES:
            leaks.append(f"{path}: image or binary file in test resources")
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
        return sanitizer.remember(json.loads((source_dir / relative).read_text(encoding="utf-8")))

    library_listing = load(LIBRARY)
    sanitizer.number_library_entries(library_listing["items"])

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

    clear_groups = load(CLEAR_GROUPS)
    for group in clear_groups:
        sanitizer.clear_group(group)
    write_json(out_dir / CLEAR_GROUPS_OUT, clear_groups)

    libraries = load(LIBRARIES)
    library_uuid = libraries[LIBRARY_INDEX]["uuid"]
    for library in libraries:
        sanitizer.library(library)
    write_json(out_dir / "libraries.json", libraries)

    for entry in library_listing["items"]:
        sanitizer.library_entry(entry)
    write_json(out_dir / f"library-{library_uuid[:8]}.json", library_listing)

    timers = load(TIMERS)
    for timer in timers:
        sanitizer.timer(timer)
    write_json(out_dir / "timers.json", timers)

    readings = load(TIMERS_CURRENT)
    for reading in readings:
        sanitizer.timer_reading(reading)
    write_json(out_dir / "timers-current.json", readings)

    for relative in STREAMS:
        sanitize_stream(sanitizer, source_dir / relative, out_dir)

    return 0 if leak_check(sanitizer, out_dir) else 1


if __name__ == "__main__":
    sys.exit(main())
