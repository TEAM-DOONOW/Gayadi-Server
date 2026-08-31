from __future__ import annotations

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOTS = (
    ROOT / "src" / "main" / "java",
    ROOT / "src" / "test" / "java",
)
RECORD_START = re.compile(r"\brecord\s+[A-Za-z_]\w*\s*\(")


def compact_unannotated_record_headers(source: str) -> str:
    lines = source.splitlines(keepends=True)
    result: list[str] = []
    index = 0

    while index < len(lines):
        line = lines[index]
        if not RECORD_START.search(line):
            result.append(line)
            index += 1
            continue

        header: list[str] = []
        balance = 0
        started = False

        while index < len(lines):
            current = lines[index]
            header.append(current)
            balance += current.count("(") - current.count(")")
            started = started or "(" in current
            index += 1
            if started and balance == 0:
                break

        if not any("@" in current for current in header):
            header = [current for current in header if current.strip()]

        result.extend(header)

    return "".join(result)


def main() -> None:
    changed = 0
    for java_root in JAVA_ROOTS:
        for path in java_root.rglob("*.java"):
            source = path.read_text(encoding="utf-8")
            formatted = compact_unannotated_record_headers(source)
            if formatted != source:
                path.write_text(formatted, encoding="utf-8")
                changed += 1

    print(f"Compacted unannotated record headers in {changed} file(s).")


if __name__ == "__main__":
    main()
