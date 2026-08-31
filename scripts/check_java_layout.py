from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "src" / "main" / "java"

ANNOTATED_DECLARATION = re.compile(
    r"^\s*@(?:Schema|NotNull|NotBlank|NotEmpty|Size|Email|Pattern|Positive|"
    r"PositiveOrZero|Negative|NegativeOrZero|Min|Max|DecimalMin|DecimalMax|"
    r"Future|FutureOrPresent|Past|PastOrPresent)\b.*\)\s+[^=]+(?:[,;)])\s*$"
)
MULTIPLE_DECLARATIONS = re.compile(
    r"^\s*(?:private|protected|public)\s+(?:final\s+)?[^;]+;\s*(?:private|protected|public)\s+"
)
INLINE_METHOD = re.compile(
    r"^\s*(?:@Override\s+)?(?:public|protected|private)\s+[^=;]+\([^;]*\)\s*\{.+}\s*$"
)
ONE_LINE_CONDITIONAL = re.compile(
    r"^\s*if\s*\(.+\)\s*(?:return|throw|[A-Za-z_]\w*\s*[+]?=).+;\s*$"
)
IMPORT = re.compile(r"^import (?:static )?[\w.]+\.([A-Za-z_]\w*);$")
PRIVATE_FIELD = re.compile(r"^    private (?:final )?[^();]+;$")
METHOD_OR_TYPE_MEMBER = re.compile(
    r"^    (?:/\*\*|@|public\s|protected\s|private\s)"
)
PUBLIC_TYPE = re.compile(
    r"^public\s+(?:final\s+|sealed\s+)?(?:class|record|enum|interface)\s+"
)
PUBLIC_METHOD = re.compile(
    r"^\s*public\s+(?!class\b|record\b|enum\b|interface\b).*\s+(\w+)\s*\("
)
RECORD_START = re.compile(r"\brecord\s+[A-Za-z_]\w*\s*\(")
REPOSITORY_CHAIN_CALL = re.compile(
    r"\.(?:param|params|query|listOfRows|stream|findFirst|map|toList|single|optional|update)\s*\("
)
REPOSITORY_ROW_ARGUMENT = re.compile(r"\(row\s*,")
DOCUMENTED_DOMAINS = {
    "auth", "coordination", "event", "expense", "favorite", "friendship",
    "invitation", "legal", "notice", "place", "schedule", "survey", "travel",
    "dashboard", "congestion", "recommendation", "route", "weather", "tourapi",
}


def has_type_javadoc(lines: list[str], declaration_index: int) -> bool:
    start = max(0, declaration_index - 20)
    context = "\n".join(lines[start:declaration_index])
    comment_start = context.rfind("/**")
    comment_end = context.rfind("*/")
    return comment_start >= 0 and comment_end > comment_start


def preceding_context(lines: list[str], index: int, size: int = 12) -> str:
    return "\n".join(lines[max(0, index - size):index])


def has_method_javadoc(lines: list[str], declaration_index: int) -> bool:
    index = declaration_index - 1
    while index >= 0 and not lines[index].strip():
        index -= 1
    while index >= 0 and lines[index].lstrip().startswith("@"):
        index -= 1
        while index >= 0 and not lines[index].strip():
            index -= 1
    if index < 0 or not lines[index].strip().endswith("*/"):
        return False
    while index >= 0 and "/**" not in lines[index]:
        index -= 1
    return index >= 0


def main() -> int:
    findings: list[str] = []

    for path in sorted(JAVA_ROOT.rglob("*.java")):
        relative = path.relative_to(ROOT)
        is_transfer_type = "dto" in path.parts or "query" in path.parts
        is_dto = "dto" in path.parts
        is_controller = path.name.endswith("Controller.java")
        is_service = path.name.endswith("Service.java")
        is_repository = path.name.endswith("Repository.java")
        domain = path.parts[path.parts.index("server") + 1] if "server" in path.parts else None
        lines = path.read_text(encoding="utf-8").splitlines()

        index = 0
        while index < len(lines):
            if not RECORD_START.search(lines[index]):
                index += 1
                continue

            header_start = index
            header: list[str] = []
            balance = 0
            while index < len(lines):
                header.append(lines[index])
                balance += lines[index].count("(") - lines[index].count(")")
                index += 1
                if balance == 0:
                    break

            if not any("@" in current for current in header) and any(
                    not current.strip() for current in header
            ):
                findings.append(
                    f"{relative}:{header_start + 1}: unannotated record components must not have blank lines"
                )

        source = "\n".join(lines)
        if is_controller and "@Tag(" not in source:
            findings.append(f"{relative}: controller has no @Tag")
        if is_dto and "@Schema(" not in source:
            findings.append(f"{relative}: DTO has no type or field @Schema")

        for number, line in enumerate(lines, start=1):
            if (domain in DOCUMENTED_DOMAINS
                    and PUBLIC_TYPE.match(line)
                    and not has_type_javadoc(lines, number - 1)):
                findings.append(f"{relative}:{number}: public type has no role Javadoc")
            if is_transfer_type and ANNOTATED_DECLARATION.match(line):
                findings.append(f"{relative}:{number}: annotation and declaration share a line")
            if MULTIPLE_DECLARATIONS.match(line):
                findings.append(f"{relative}:{number}: multiple fields share a line")
            if INLINE_METHOD.match(line):
                findings.append(f"{relative}:{number}: method body is compressed onto one line")
            if ONE_LINE_CONDITIONAL.match(line):
                findings.append(
                    f"{relative}:{number}: one-line conditional must use braces"
                )
            if is_repository and len(REPOSITORY_CHAIN_CALL.findall(line)) > 1:
                findings.append(
                    f"{relative}:{number}: repository chain calls must be split across lines"
                )
            if is_repository and len(REPOSITORY_ROW_ARGUMENT.findall(line)) > 1:
                findings.append(
                    f"{relative}:{number}: repository row mapping arguments must be split across lines"
                )
            imported = IMPORT.match(line)
            if imported and len(re.findall(rf"\b{re.escape(imported.group(1))}\b", source)) == 1:
                findings.append(f"{relative}:{number}: unused import")
            if (number < len(lines)
                    and line == "    }"
                    and METHOD_OR_TYPE_MEMBER.match(lines[number])
                    and lines[number].strip() != "};"):
                findings.append(
                    f"{relative}:{number + 1}: members must have a blank line between them"
                )
            if (number < len(lines)
                    and line.lstrip().startswith("@")
                    and lines[number].lstrip().startswith("/**")):
                findings.append(
                    f"{relative}:{number + 1}: Javadoc must precede method annotations"
                )
            if (is_dto
                    and PRIVATE_FIELD.match(line)
                    and number < len(lines)
                    and (lines[number].startswith("    @")
                         or PRIVATE_FIELD.match(lines[number]))):
                findings.append(
                    f"{relative}:{number + 1}: DTO fields must have a blank line between them"
                )

            method_name = None
            stripped = line.strip()
            if stripped.startswith("public ") and "(" in stripped:
                prefix = stripped.split("(", 1)[0]
                if not any(token in prefix for token in (" class ", " record ", " enum ", " interface ")):
                    method_name = prefix.split()[-1]
            is_constructor = method_name is not None and method_name[:1].isupper()
            if method_name and not is_constructor and is_controller:
                context = preceding_context(lines, number - 1, 80)
                if "@Operation(" not in context:
                    findings.append(f"{relative}:{number}: HTTP API has no @Operation")
            if method_name and not is_constructor and (is_service or is_repository):
                type_name = path.stem
                if method_name != type_name:
                    if not has_method_javadoc(lines, number - 1):
                        findings.append(
                            f"{relative}:{number}: public service/repository method has no Javadoc"
                        )

        for number in range(2, len(lines)):
            if not lines[number].strip() and not lines[number - 1].strip() and not lines[number - 2].strip():
                findings.append(f"{relative}:{number + 1}: more than two consecutive blank lines")

    if findings:
        print("\n".join(findings))
        print(f"\n{len(findings)} Java layout issue(s) found.")
        return 1

    print("Java layout check passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
