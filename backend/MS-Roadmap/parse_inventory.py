import os
import re

def parse_java(filepath):
    with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
        raw = f.read()

    m = re.search(r'package\s+([\w.]+)\s*;', raw)
    pkg = m.group(1) if m else 'unknown'

    cleaned = raw
    cleaned = re.sub(r'//.*$', '', cleaned, flags=re.MULTILINE)
    cleaned = re.sub(r'/\*.*?\*/', '', cleaned, flags=re.DOTALL)

    decl_match = re.search(
        r'(?:public\s+|private\s+|protected\s+)?(?:abstract\s+|final\s+)?(class|interface|enum|record)\s+(\w+)\b',
        cleaned
    )
    if not decl_match:
        return None

    type_kw = decl_match.group(1)
    cls_name = decl_match.group(2)

    # Find extends
    ext_match = re.search(r'(?:class|enum)\s+' + re.escape(cls_name) + r'(?:\s+extends\s+(\w+))?', cleaned)
    extends = ext_match.group(1) if ext_match and ext_match.group(1) else 'none'

    # Find implements
    imp_match = re.search(
        r'(?:class|interface|enum|record)\s+' + re.escape(cls_name) +
        r'(?:\s+extends\s+[\w\s,<>]+?)?\s+implements\s+(([\w\s,<>]+?)\s*)\{',
        cleaned
    )
    if imp_match:
        implements = re.sub(r'\s+', '', imp_match.group(1).strip())
    else:
        implements = 'none'

    # Find class body
    decl_after = decl_match.end()
    after_decl = cleaned[decl_after:]
    brace_start_pos = after_decl.find('{')
    if brace_start_pos == -1:
        return {'package': pkg, 'class_name': cls_name, 'extends': extends,
                'implements': implements, 'fields': [], 'methods': [], 'type': type_kw}

    brace_idx = decl_after + brace_start_pos + 1
    depth = 1
    i = brace_idx
    while i < len(cleaned) and depth > 0:
        if cleaned[i] == '{':
            depth += 1
        elif cleaned[i] == '}':
            depth -= 1
        i += 1
    body = cleaned[brace_idx:i-1]

    methods = []
    fields = []

    lines = body.split('\n')
    brace_depth = 0
    skip = False

    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith('//') or stripped.startswith('import '):
            continue
        if stripped.startswith('@'):
            continue
        if stripped.startswith('package '):
            continue

        open_b = stripped.count('{')
        close_b = stripped.count('}')

        if skip:
            brace_depth += open_b - close_b
            if brace_depth <= 0:
                skip = False
                brace_depth = 0
            continue

        # Constructor
        if re.search(r'(?:public|private|protected).*' + re.escape(cls_name) + r'\s*\(', stripped):
            if '{' in stripped:
                sig = stripped[:stripped.index('{')].strip()
                methods.append(sig)
                brace_depth = open_b - close_b
                if brace_depth > 0:
                    skip = True
                continue

        # Method (has return type, name, params, then { or ;)
        method_pat = re.match(
            r'((?:public|private|protected|static|final|abstract|default|synchronized|\s)*'
            r'(?:void|int|boolean|String|long|double|float|byte|short|char|[A-Z]\w*(?:<[^>]*>)?(?:\[\])*)'
            r'(?:\[\])*\s+'
            r'\w+\s*'
            r'\([^)]*\)'
            r'(?:\s+throws\s+[\w\s,]+)?'
            r')\s*[{;]',
            stripped
        )
        if method_pat:
            sig = method_pat.group(1).strip()
            if '{' in stripped:
                methods.append(sig)
                brace_depth = open_b - close_b
                if brace_depth > 0:
                    skip = True
            else:
                methods.append(sig)
            continue

        # Field (has ; but no {)
        if ';' in stripped and '{' not in stripped:
            field_pat = re.match(
                r'(?:public|private|protected|static|final|transient|volatile|\s)*\s*'
                r'([\w<>\[\],\s.?]+?)\s+'
                r'(\w+)\s*'
                r'(?:=\s*[^;]+)?\s*;',
                stripped
            )
            if field_pat:
                ftype = field_pat.group(1).strip()
                fname = field_pat.group(2).strip()
                keywords = {'', 'public', 'private', 'protected', 'static', 'final',
                    'public static', 'private static', 'protected static',
                    'public final', 'private final',
                    'public static final', 'private static final',
                    'protected static final', 'static final'}
                if ftype not in keywords:
                    fields.append(f"{fname} : {ftype}")

    return {
        'package': pkg,
        'class_name': cls_name,
        'type': type_kw,
        'extends': extends,
        'implements': implements,
        'fields': fields,
        'methods': methods
    }

base = r'C:\Users\MSI\Desktop\New folder\SmartHire\MS-Roadmap'
result = os.popen(f'find "{base}/src/main/java" -type f -name "*.java"').read()
files = sorted([f.strip() for f in result.strip().split('\n') if f.strip()])

for fpath in files:
    rel = fpath.replace('\\', '/')
    if base in rel:
        rel = rel[rel.index(base)+len(base)+1:]

    info = parse_java(fpath)
    if info is None:
        continue

    print(f"FILE: {rel}")
    print(f"PACKAGE: {info['package']}")
    print(f"CLASS: {info['class_name']}")
    print(f"EXTENDS: {info['extends']}")
    print(f"IMPLEMENTS: {info['implements']}")
    print("FIELDS:")
    for fld in info['fields']:
        print(f"  - {fld}")
    print("METHODS:")
    for mt in info['methods']:
        print(f"  - {mt}")
    print("")
