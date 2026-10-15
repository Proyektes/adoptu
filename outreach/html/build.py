"""Renders outreach/*-email.md into email-client-safe HTML (tables + inline styles)."""
import html, re, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEAL, INK, MUTED, BG = "#0f766e", "#1f2933", "#52606d", "#f1f5f4"
# Embedded as a data: URI - Thunderbird's composer won't load remote images, and on send it turns
# data: images into inline (cid:) attachments, so recipients see the logo without "load images".
import base64
LOGO = "data:image/png;base64," + base64.b64encode((ROOT / "html" / "logo-192.png").read_bytes()).decode()

def inline(t):
    t = html.escape(t, quote=False)
    t = re.sub(r"\[([^\]]+)\]\((https?://[^)]+)\)", rf'<a href="\2" style="color:{TEAL};font-weight:700;text-decoration:none">\1</a>', t)
    t = re.sub(r"\*\*(.+?)\*\*", rf'<strong style="color:{INK}">\1</strong>', t)
    return t

def parse(md):
    parts = md.split("\n---\n")
    i = next(i for i, p in enumerate(parts) if "**Asunto:**" in p)
    head, body = parts[i], parts[i + 1]
    meta = {k.lower(): v.strip() for k, v in re.findall(r"\*\*(Para|CC|Asunto):\*\*\s*(.*)", head)}
    return meta, body.strip()

def render_body(body):
    out, para, items = [], [], []
    def flush():
        nonlocal para, items
        if para:
            out.append(f'<p style="margin:0 0 14px;color:{MUTED};font-family:Helvetica,Arial,sans-serif;font-size:15px;line-height:1.55">{inline(" ".join(para))}</p>'); para = []
        if items:
            rows = "".join(
                f'<tr><td valign="top" style="padding:0 10px 10px 0;width:26px">'
                f'<div style="width:24px;height:24px;line-height:24px;border-radius:12px;background:{TEAL};color:#fff;font-size:12px;font-weight:700;text-align:center">{n}</div></td>'
                f'<td valign="top" style="padding:2px 0 10px;color:{MUTED};font-family:Helvetica,Arial,sans-serif;font-size:15px;line-height:1.55">{inline(t)}</td></tr>' for n, t in items)
            out.append(f'<table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 8px">{rows}</table>'); items = []
    lines = body.splitlines()
    # signature: everything from "Atentamente," on
    sig_i = next((i for i, l in enumerate(lines) if l.strip().startswith("Atentamente")), len(lines))
    for l in lines[:sig_i]:
        s = l.strip()
        m = re.match(r"(\d+)\.\s+(.*)", s)
        if not s: flush()
        elif m: 
            if para: flush()
            items.append((m.group(1), m.group(2)))
        elif items and l.startswith("   "): items[-1] = (items[-1][0], items[-1][1] + " " + s)
        else:
            if items: flush()
            para.append(s)
    flush()
    return "\n".join(out)

def page(meta, body_html):
    subject = html.escape(meta.get("asunto", "Adopt-U"))
    return f'''<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>{subject}</title></head>
<body style="margin:0;padding:0;background:{BG}">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:{BG}"><tr><td align="center" style="padding:24px 12px">
<table role="presentation" width="640" cellpadding="0" cellspacing="0" style="max-width:640px;width:100%;background:#ffffff;border-radius:10px;overflow:hidden;font-family:Helvetica,Arial,sans-serif;font-size:15px;line-height:1.55;color:{MUTED}">
<tr><td style="background:{TEAL};padding:20px 28px">
  <table role="presentation" cellpadding="0" cellspacing="0"><tr>
    <td style="padding-right:14px"><img src="{LOGO}" width="56" height="56" alt="Adopt-U" style="display:block;border-radius:10px"></td>
    <td><div style="color:#ffffff;font-size:24px;font-weight:700">Adopt-U</div>
        <div style="color:#fcd34d;font-size:13px;font-weight:700">Sin costo · Sin fines de lucro · Sin publicidad</div></td>
  </tr></table>
</td></tr>
<tr><td style="padding:28px 28px 8px;background:#ffffff;color:{MUTED}">
{body_html}
</td></tr>
<tr><td style="padding:0 28px 26px;background:#ffffff;color:{MUTED};font-family:Helvetica,Arial,sans-serif;font-size:15px">
  <p style="margin:0 0 18px;color:{MUTED}">Atentamente,</p>
  <table role="presentation" cellpadding="0" cellspacing="0" style="border-left:3px solid {TEAL}"><tr><td style="padding:2px 0 2px 12px;color:{MUTED};font-family:Helvetica,Arial,sans-serif;font-size:15px">
    <div style="color:{INK};font-weight:700;font-size:16px">Iván Gómez</div>
    <div style="color:{MUTED}">Fundador, Adopt-U</div>
    <div><a href="mailto:adopt-u@adopt-u.org" style="color:{TEAL};text-decoration:none">adopt-u@adopt-u.org</a> · <a href="https://adopt-u.org" style="color:{TEAL};text-decoration:none">adopt-u.org</a></div>
  </td></tr></table>
</td></tr>
<tr><td align="center" style="background:{TEAL};padding:16px 28px">
  <a href="https://adopt-u.org/register" style="display:inline-block;background:#ffffff;color:{TEAL};font-weight:700;text-decoration:none;padding:10px 22px;border-radius:6px">Registrarse en adopt-u.org</a>
</td></tr>
</table></td></tr></table>
</body></html>
'''

for name in sys.argv[1:]:
    md = (ROOT / f"{name}.md").read_text()
    meta, body = parse(md)
    (ROOT / "html" / f"{name}.html").write_text(page(meta, render_body(body)))
    print(name, "|", meta.get("asunto"))
