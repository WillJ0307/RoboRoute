import re
import struct as _struct

_TYPES = {
    "bool": ("?", 1, False),
    "boolean": ("?", 1, False),
    "char": ("b", 1, True),
    "int8": ("b", 1, True),
    "int16": ("h", 2, True),
    "int32": ("i", 4, True),
    "int64": ("q", 8, True),
    "uint8": ("B", 1, False),
    "uint16": ("H", 2, False),
    "uint32": ("I", 4, False),
    "uint64": ("Q", 8, False),
    "float": ("f", 4, False),
    "float32": ("f", 4, False),
    "double": ("d", 8, False),
    "float64": ("d", 8, False),
}

_TOPIC_RE = re.compile(r"^struct:(.+?)(\[\])?$")
_FIELD_RE = re.compile(
    r"^(?:(?:enum\s*)?\{(?P<enum>[^}]*)\}\s*)?"
    r"(?P<optkw>optional\s+)?"
    r"(?P<type>[^\s\[\]?]+)"
    r"(?:\[\s*(?P<count>\?|\d+)\s*\])?"
    r"(?P<opt>\?)?"
    r"\s+"
    r"(?P<name>[A-Za-z_]\w*)"
    r"\s*(?:\[\s*(?P<count2>\?|\d+)\s*\])?"
    r"\s*(?::\s*(?P<bits>\d+))?\s*;?\s*$"
)
_INLINE_STRUCT_RE = re.compile(r"struct\s+([A-Za-z_]\w*)\s*\{")


def _strip_wrapper(text):
    text = re.sub(r"^struct\s+[A-Za-z_]\w*\s*\{?", "", text.strip(), count=1)
    return re.sub(r"}\s*$", "", text.strip())


def struct_name(schema):
    match = re.match(r"^\s*struct\s+([A-Za-z_]\w*)", schema)
    return match.group(1) if match else None


def _extract_nested(text):
    """Replace inline `struct Name { ... } field;` defs with `Name field;` refs.

    Returns (text_with_refs, {nested_name: full_nested_schema}).
    """
    nested = {}
    pos = 0
    while True:
        match = _INLINE_STRUCT_RE.search(text, pos)
        if match is None:
            break
        name = match.group(1)
        open_brace = match.end() - 1
        depth = 1
        i = open_brace + 1
        while i < len(text) and depth:
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
            i += 1
        if depth:
            raise ValueError(f"unbalanced inline struct: {name}")
        close = i - 1
        body = text[open_brace + 1 : close].strip()
        if _INLINE_STRUCT_RE.search(body):
            pos = match.start() + 1
            continue
        j = close + 1
        while j < len(text) and text[j] in " \t\r\n":
            j += 1
        k = j
        while k < len(text) and text[k] != ";":
            k += 1
        tail = text[j:k].strip()
        nested[name] = f"struct {name} {{{body}}}"
        reference = f"{name} {tail};"
        text = text[: match.start()] + reference + text[k + 1 :]
        pos = 0
    return text, nested


def _parse_enum(spec):
    if spec is None:
        return None
    mapping = {}
    for item in spec.split(","):
        item = item.strip()
        if not item:
            continue
        parts = [p.strip() for p in item.split("=", 1)]
        if len(parts) == 2:
            try:
                mapping[int(parts[1], 0)] = parts[0]
            except ValueError:
                continue
    return mapping or None


def parse_schema(schema):
    text, _ = _extract_nested(_strip_wrapper(schema))
    fields = []
    for decl in text.split(";"):
        decl = decl.strip()
        if not decl:
            continue
        m = _FIELD_RE.match(decl)
        if not m:
            raise ValueError(f"unsupported schema declaration: {decl!r}")
        g = m.groupdict()
        ftype = g["type"]
        count_s = g["count"] or g["count2"]
        vla = count_s == "?"
        if count_s and count_s != "?":
            count = int(count_s)
        else:
            count = 1
        bits = int(g["bits"]) if g["bits"] else None
        enum = _parse_enum(g["enum"])
        optional = bool(g["optkw"] or g["opt"])
        is_struct = ftype not in _TYPES
        field = {
            "name": g["name"],
            "type": ftype,
            "count": count,
            "vla": vla,
            "optional": optional,
            "bits": bits,
            "enum": enum,
            "struct": is_struct,
        }
        if bits is not None and is_struct:
            raise ValueError(f"bit-field must be integer/bool type: {decl!r}")
        if bits is not None and ftype == "bool" and bits != 1:
            raise ValueError("bool bit-field must be 1 bit")
        if bits is not None and bits > _TYPES[ftype][1] * 8:
            raise ValueError(f"bit-field larger than storage type: {decl!r}")
        if (vla or optional) and bits is not None:
            raise ValueError(
                f"bit-field cannot be variable-length or optional: {decl!r}"
            )
        if vla and optional:
            raise ValueError(
                f"field cannot be both variable-length and optional: {decl!r}"
            )
        fields.append(field)
    return fields


def _sign_extend(val, width):
    sign = 1 << (width - 1)
    return (val ^ sign) - sign if val & sign else val


def _read_bits(cur, width):
    shift = cur["start_bit"]
    val = (cur["raw"] >> shift) & ((1 << width) - 1)
    cur["start_bit"] += width
    return val


class SchemaRegistry:
    def __init__(self):
        self._fields = {}
        self._schemas = {}
        self._aliases = {}

    def register(self, struct_name, schema, _depth=0):
        if _depth > 8:
            return False
        if "{" in schema and not schema.strip().endswith("}"):
            self._fields.pop(struct_name, None)
            return False
        self._schemas[struct_name] = schema

        try:
            text, nested = _extract_nested(_strip_wrapper(schema))
            fields = parse_schema(text)
        except (KeyError, TypeError, ValueError):
            self._fields.pop(struct_name, None)
            return False

        self._fields[struct_name] = fields
        alias = None
        if struct_name.startswith("struct:"):
            alias = struct_name[len("struct:") :]
        elif struct_name.startswith("photonstruct:"):
            alias = struct_name[len("photonstruct:") :]
        if alias and alias not in self._fields and alias not in self._aliases:
            self._aliases[alias] = struct_name

        names = [struct_name]
        for nested_name, nested_schema in nested.items():
            if nested_name not in self._fields and self.register(
                nested_name, nested_schema, _depth + 1
            ):
                names.append(nested_name)
        return names

    _TYPE_PREFIXES = ("struct:", "photonstruct:")

    def _resolve(self, name):
        if name in self._fields:
            return name
        for prefix in self._TYPE_PREFIXES:
            if name.startswith(prefix):
                base = name[len(prefix) :]
                if base in self._fields:
                    return base
                break
        return self._aliases.get(name)

    def has_type(self, type_str):
        if self._resolve(type_str) is not None:
            return True
        return bool(
            type_str.endswith("[]") and self._resolve(type_str[:-2]) is not None
        )

    def get_schema(self, struct_name):
        base = struct_name.removesuffix("[]")

        if struct_name in self._schemas:
            return self._schemas[struct_name]
        if base in self._schemas:
            return self._schemas[base]

        resolved = self._resolve(base)
        return self._schemas.get(resolved) if resolved else None

    def decode_struct(self, struct_name, buf, pos=0):
        resolved = self._resolve(struct_name)
        if resolved is None:
            raise KeyError(f"unknown struct schema: {struct_name}")
        out = {}
        cur = None
        for f in self._fields[resolved]:
            if f["bits"] is not None:
                val, cur, pos = self._decode_bitfield(buf, pos, f, cur)
                out[f["name"]] = val
            else:
                pos = self._flush(cur, pos)
                cur = None
                out[f["name"]], pos = self._decode_field(buf, pos, f)
        pos = self._flush(cur, pos)
        return out, pos

    def encode_struct(self, struct_name, data):
        resolved = self._resolve(struct_name)
        if resolved is None:
            raise KeyError(f"unknown struct schema: {struct_name}")
        out = bytearray()
        cur = None
        for f in self._fields[resolved]:
            name = f["name"]
            if name not in data:
                raise ValueError(f"missing field: {name}")
            if f["bits"] is not None:
                cur, out = self._encode_bitfield(out, cur, f, data[name])
            else:
                cur = self._flush_encode(cur, out)
                self._encode_field(out, f, data[name])
        self._flush_encode(cur, out)
        return bytes(out)

    def encode_type(self, type_str, data):
        if self._resolve(type_str) is not None:
            return self.encode_struct(type_str, data)
        if type_str.endswith("[]"):
            base = type_str[:-2]
            if self._resolve(base) is not None:
                return b"".join(self.encode_struct(base, v) for v in data)
        raise KeyError(f"unknown struct schema: {type_str}")

    def decode_type(self, type_str, buf):
        if self._resolve(type_str) is not None:
            val, _ = self.decode_struct(type_str, buf, 0)
            return val
        if type_str.endswith("[]"):
            base = type_str[:-2]
            if self._resolve(base) is not None:
                _, elem_size = self.decode_struct(base, buf, 0)
                if elem_size <= 0:
                    return []
                out = []
                pos = 0
                while pos + elem_size <= len(buf):
                    val, pos = self.decode_struct(base, buf, pos)
                    out.append(val)
                return out
        raise KeyError(f"unknown struct schema: {type_str}")

    @staticmethod
    def _flush(cur, pos):
        return pos + cur["size"] if cur is not None else pos

    def _decode_field(self, buf, pos, f):
        if f["vla"]:
            n = buf[pos]
            pos += 1
            vals = []
            for _ in range(n):
                val, pos = self._decode_one(buf, pos, f)
                vals.append(val)
            return vals, pos
        if f["optional"]:
            present = buf[pos]
            pos += 1
            if not present:
                return None, pos
            return self._decode_one(buf, pos, f)
        if f["count"] > 1 and f["type"] != "char":
            vals = []
            for _ in range(f["count"]):
                val, pos = self._decode_one(buf, pos, f)
                vals.append(val)
            return vals, pos
        return self._decode_one(buf, pos, f)

    def _decode_one(self, buf, pos, f):
        t = f["type"]
        if f["struct"]:
            return self.decode_struct(t, buf, pos)
        if t == "char" and f["count"] > 1:
            s = buf[pos : pos + f["count"]].split(b"\x00", 1)[0]
            return s.decode("utf-8", "replace"), pos + f["count"]
        fmt, sz, _ = _TYPES[t]
        val = _struct.unpack_from("<" + fmt, buf, pos)[0]
        pos += sz
        if f["enum"] is not None and isinstance(val, (int, bool)) and val in f["enum"]:
            val = f["enum"][val]
        return val, pos

    def _decode_bitfield(self, buf, pos, f, cur):
        is_bool = f["type"] == "bool"
        size = 1 if is_bool else _TYPES[f["type"]][1]
        width = f["bits"]
        if cur is not None:
            remaining = cur["size"] * 8 - cur["start_bit"]
            if is_bool:
                if remaining >= 1:
                    return _read_bits(cur, 1), cur, pos
            elif cur["size"] == size and remaining >= width:
                return self._post_int(_read_bits(cur, width), f), cur, pos
        pos = self._flush(cur, pos)
        storage_size = size if not is_bool else 1
        cur = {
            "size": storage_size,
            "start_bit": 0,
            "raw": int.from_bytes(buf[pos : pos + storage_size], "little"),
        }
        if is_bool:
            return _read_bits(cur, 1), cur, pos
        return self._post_int(_read_bits(cur, width), f), cur, pos

    def _post_int(self, val, f):
        if f["type"] in ("int8", "int16", "int32", "int64"):
            val = _sign_extend(val, f["bits"])
        if f["enum"] is not None and val in f["enum"]:
            val = f["enum"][val]
        return val

    def _encode_field(self, out, f, val):
        if f["vla"]:
            out.append(len(val))
            for item in val:
                self._encode_one(out, f, item)
            return
        if f["optional"]:
            if val is None:
                out.append(0)
                return
            out.append(1)
            self._encode_one(out, f, val)
            return
        if f["count"] > 1 and f["type"] != "char":
            for item in val:
                self._encode_one(out, f, item)
            return
        if f["type"] == "char" and f["count"] > 1:
            encoded = val.encode("utf-8", "replace")
            if len(encoded) > f["count"] - 1:
                encoded = encoded[: f["count"] - 1]
            out += encoded
            out += b"\x00" * (f["count"] - len(encoded))
            return
        self._encode_one(out, f, val)

    def _encode_one(self, out, f, val):
        if f["struct"]:
            out += self.encode_struct(f["type"], val)
            return
        if f["enum"] is not None and isinstance(val, str):
            by_name = {value: key for key, value in f["enum"].items()}
            if val not in by_name:
                raise ValueError(f"unknown enum value: {val}")
            val = by_name[val]
        fmt, _, _ = _TYPES[f["type"]]
        out += _struct.pack("<" + fmt, val)

    def _encode_bitfield(self, out, cur, f, val):
        is_bool = f["type"] == "bool"
        size = 1 if is_bool else _TYPES[f["type"]][1]
        width = f["bits"]
        if cur is not None:
            remaining = cur["size"] * 8 - cur["start_bit"]
            if is_bool:
                if remaining >= 1:
                    cur["raw"] |= (1 if val else 0) << cur["start_bit"]
                    cur["start_bit"] += 1
                    return cur, out
            elif cur["size"] == size and remaining >= width:
                cur["raw"] |= self._enumerize(val, f) << cur["start_bit"]
                cur["start_bit"] += width
                return cur, out
        out += cur["raw"].to_bytes(cur["size"], "little") if cur is not None else b""
        storage_size = size if not is_bool else 1
        cur = {"size": storage_size, "start_bit": 0, "raw": 0}
        cur["raw"] |= self._enumerize(val, f) << cur["start_bit"]
        cur["start_bit"] += width
        return cur, out

    def _enumerize(self, val, f):
        if f["enum"] is not None and isinstance(val, str):
            by_name = {value: key for key, value in f["enum"].items()}
            if val not in by_name:
                raise ValueError(f"unknown enum value: {val}")
            val = by_name[val]
        return int(val) & ((1 << f["bits"]) - 1)

    @staticmethod
    def _flush_encode(cur, out):
        if cur is not None:
            out += cur["raw"].to_bytes(cur["size"], "little")
