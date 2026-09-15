"""Generate the editable YAML command catalogue from documentation/COMMANDS.md. No dependencies."""
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
entries = {}
for line in (root / "documentation/COMMANDS.md").read_text(encoding="utf-8").splitlines():
    if not line.startswith("|"):
        continue
    cells = re.split(r"(?<!\\)\|", line.strip().strip("|"))
    if len(cells) < 2:
        continue
    for command in re.findall(r"`(/(?:es|actor|scene|nickname|actors|kits)\b[^`]*)`", cells[0]):
        command = command.replace(r"\|", "|").strip()
        first, *rest = command.split(" ", 1)
        if first != "/es":
            shortcut = {"/actors": "menu actors", "/kits": "kits"}.get(first, first[1:])
            command = "/es " + shortcut + (" " + rest[0] if rest else "")
        description = cells[1].strip().replace("`", "").replace("**", "").replace(r"\|", "|")
        example = ""
        if len(cells) > 2:
            found = re.search(r"`(/[^`]+)`", cells[2])
            if found:
                example = found[1].replace(r"\|", "|")
        entries[command] = {"syntax": command, "description": description, "example": example}

lines = ["# Command syntax help shown after an invalid or incomplete command.",
         "# Edit descriptions/examples to localize. Regenerate defaults with scripts/update-command-help.py.",
         "# syntax uses <required>, [optional], and | for alternatives; keep the command words intact.",
         "# description explains the effect; example is one complete command the player can adapt.",
         "schema: 1", "commands:"]
for entry in entries.values():
    for i, (key, value) in enumerate(entry.items()):
        lines.append(("  - " if i == 0 else "    ") + key + ": " + json.dumps(value, ensure_ascii=False))
(root / "src/main/resources/command-help.yml").write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"Wrote {len(entries)} command forms.")
