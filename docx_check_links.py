import os,re,sys

root = 'docx'
link_re = re.compile(r'\]\((?!https?://)([^)]+)\)')

md_files = []
for dp, _, fs in os.walk(root):
    for f in fs:
        if f.endswith('.md'):
            md_files.append(os.path.join(dp, f))

bad = []
for fp in md_files:
    base = os.path.dirname(fp)
    with open(fp, 'r', encoding='utf-8') as fh:
        txt = fh.read()
    for m in link_re.finditer(txt):
        p = m.group(1).strip()
        if p.startswith('#') or p.startswith('mailto:') or p.startswith('javascript:'):
            continue
        p = p.split('#', 1)[0].split('?', 1)[0].strip()
        if not p:
            continue
        p = p.replace('\\', '/')
        if re.match(r'^[a-zA-Z]+:', p) or p.startswith('//'):
            continue
        target = os.path.normpath(os.path.join(base, p))
        if not os.path.exists(target):
            bad.append((fp, p))

if bad:
    for fp, p in bad:
        print(f'BAD\t{fp}\t{p}')
    sys.exit(1)

print('OK')
