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
    fields = []
    for decl in schema.split(";"):
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

    def register(self, struct_name, schema):
        self._schemas[struct_name] = schema

        try:
            fields = parse_schema(schema)
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

        return True

    def _resolve(self, name):
        if name in self._fields:
            return name
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
