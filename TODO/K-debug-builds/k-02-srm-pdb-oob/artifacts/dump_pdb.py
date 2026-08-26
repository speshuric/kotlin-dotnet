#!/usr/bin/env python3
"""Побайтовый разбор standalone portable PDB (потоки, таблицы, Document).

Использование: python3 dump_pdb.py <file.pdb>
Без зависимостей; для разбора отладки K-02 (см. README.md рядом).
"""
import struct
import sys


def main(path: str) -> None:
    data = open(path, 'rb').read()
    off = data.find(b'BSJB')
    p = off + 4
    p += 8  # major/minor/reserved
    verlen = struct.unpack_from('<i', data, p)[0]
    p += 4
    ver = data[p:p + verlen].rstrip(b'\0')
    p += verlen
    nstreams = struct.unpack_from('<H', data, p + 2)[0]
    p += 4
    print(f'BSJB@{off:#x} version={ver.decode(errors="replace")!r} streams={nstreams}')
    st = {}
    for _ in range(nstreams):
        o, size = struct.unpack_from('<II', data, p)
        p += 8
        e = data.index(b'\0', p)
        name = data[p:e].decode()
        p = e + 1
        p = (p + 3) // 4 * 4
        st[name] = (o, size)
        print(f'  {name:9s} off={o:#x} size={size}')

    tofs, tsize = st['#~']
    q = off + tofs
    q += 4  # reserved
    heapsizes = data[q + 2]
    q += 4  # major/minor/heapsizes/rid
    valid = struct.unpack_from('<Q', data, q)[0]
    q += 8
    sortedm = struct.unpack_from('<Q', data, q)[0]
    q += 8
    bits = [i for i in range(64) if valid >> i & 1]
    counts = {i: struct.unpack_from('<i', data, q + 4 * n)[0] for n, i in enumerate(bits)}
    q += 4 * len(bits)
    print(f'  heapSizes flag={heapsizes:#x} valid={valid:#x} sorted={sortedm:#x}')
    print('  counts:', {hex(i): c for i, c in counts.items()})

    if counts.get(0x30):
        name, algo, h, lang = struct.unpack_from('<HHHH', data, q)
        print(f'  Document row @ stream+{q - (off + tofs)}: name={name} hashAlgo={algo} hash={h} lang={lang}')
        bo, bs = st['#Blob']
        blob = data[off + bo:off + bo + bs]
        print(f'  #Blob heap ({bs}B): {blob.hex()}')
        for label, idx in (('name', name), ('hash', h)):
            if idx < len(blob):
                n = blob[idx]
                head = blob[idx + 1:idx + 1 + min(n, 40)]
                print(f'    blob[{idx}] len={n} head={head.hex()} ascii={head.decode(errors="replace")!r}')


if __name__ == '__main__':
    main(sys.argv[1])
