#!/usr/bin/env python3
"""Generates an AERF release status / scoping report as a PDF.

Written for the v0.4.1 report (what the patch contains, how each open
question was resolved, what remains, and the proposed v0.4.2 / v0.5 split)
and kept in the repository so the next release can reuse it rather than
rebuild the layout from scratch.

    pip install reportlab            # only dependency
    python3 docs/reports/build-status-report.py [output.pdf]

Default output is aerf-v0.4.1-status-report.pdf next to this script.

WHAT TO EDIT FOR THE NEXT RELEASE
---------------------------------
Everything below the "CONTENT" banner is the report itself; everything
above it is layout machinery (fonts, palette, table/callout builders,
page chrome) that should carry over unchanged. The content is assembled
from a handful of clearly-named data lists, so a new edition is mostly a
matter of updating those rather than touching prose flow:

    meta_rows      cover facts - repo, branch, head commit, state
    stat_rows      the four headline numbers on the summary page
    amend_rows     one row per v0.4.1-patch amendment
    inc_groups     increments, grouped by phase
    mod_rows       module / test counts (from a real `mvn -B test` run)
    qa(...) calls  one block per RESOLVED open question
    block_rows     outstanding questions grouped by what blocks them
    out_rows       the full outstanding list, with proposed target release
    v442 / v5      the recommended split and its reasoning
    commits        the commit trail appendix

The palette deliberately mirrors the scan dashboard's own design tokens
(dashboard/style.css) so the two read as one project.

VERIFY BEFORE SHIPPING
----------------------
Every figure in the report is meant to come from the committed documents
and an actual test run, never from recollection. After regenerating,
render the pages and read them - past editions caught real problems that
way (orphaned headings, half-empty pages, a subscript rendered as a
literal underscore):

    python3 -c "import pypdfium2 as p; d=p.PdfDocument('out.pdf'); \\
        [d[i].render(scale=1.5).to_pil().save(f'pg{i+1:02d}.png') \\
         for i in range(len(d))]"

Note: ReportLab's built-in fonts have no glyphs for Unicode sub/superscript
characters - they render as solid black boxes. Use <sub>/<super> markup in
Paragraph text instead, as the drift and E_total/E_inv entries below do.
"""

import os
import sys

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT, TA_JUSTIFY
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (BaseDocTemplate, Frame, KeepTogether, PageBreak,
                                Paragraph, Spacer, Table, TableStyle, PageTemplate)

# ---------------------------------------------------------------- fonts
FONT_DIR = "/usr/share/fonts/truetype/dejavu"
pdfmetrics.registerFont(TTFont("Body", f"{FONT_DIR}/DejaVuSans.ttf"))
pdfmetrics.registerFont(TTFont("Body-Bold", f"{FONT_DIR}/DejaVuSans-Bold.ttf"))
pdfmetrics.registerFont(TTFont("Mono", f"{FONT_DIR}/DejaVuSansMono.ttf"))
pdfmetrics.registerFont(TTFont("Mono-Bold", f"{FONT_DIR}/DejaVuSansMono-Bold.ttf"))
pdfmetrics.registerFontFamily("Body", normal="Body", bold="Body-Bold",
                              italic="Body", boldItalic="Body-Bold")

# ------------------------------------------------- palette (dashboard tokens)
INK = colors.HexColor("#0b0b0b")
INK_SOFT = colors.HexColor("#52514e")
MUTED = colors.HexColor("#898781")
RULE = colors.HexColor("#e1e0d9")
SURFACE = colors.HexColor("#f9f9f7")
ACCENT = colors.HexColor("#184f95")
ACCENT_LT = colors.HexColor("#2a78d6")
ACCENT_WASH = colors.HexColor("#eef4fc")
GOOD = colors.HexColor("#0ca30c")
WARN = colors.HexColor("#b8850f")
CRIT = colors.HexColor("#d03b3b")
GOOD_WASH = colors.HexColor("#edf7ed")
WARN_WASH = colors.HexColor("#fdf6e6")

PAGE_W, PAGE_H = A4
MARGIN = 20 * mm
CONTENT_W = PAGE_W - 2 * MARGIN


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


# ---------------------------------------------------------------- styles
ss = getSampleStyleSheet()

body = ParagraphStyle("body", parent=ss["Normal"], fontName="Body", fontSize=9.2,
                      leading=13.6, textColor=INK, alignment=TA_JUSTIFY,
                      spaceAfter=7)
body_l = ParagraphStyle("body_l", parent=body, alignment=TA_LEFT)
lede = ParagraphStyle("lede", parent=body, fontSize=10.4, leading=15.6,
                      textColor=INK_SOFT, spaceAfter=10)

h1 = ParagraphStyle("h1", parent=ss["Normal"], fontName="Body-Bold", fontSize=16,
                    leading=20, textColor=ACCENT, spaceBefore=4, spaceAfter=3)
h1_num = ParagraphStyle("h1_num", parent=ss["Normal"], fontName="Body-Bold",
                        fontSize=8.5, leading=11, textColor=ACCENT_LT,
                        spaceAfter=2)
h2 = ParagraphStyle("h2", parent=ss["Normal"], fontName="Body-Bold", fontSize=11,
                    leading=15, textColor=INK, spaceBefore=13, spaceAfter=5)
h3 = ParagraphStyle("h3", parent=ss["Normal"], fontName="Body-Bold", fontSize=9.4,
                    leading=13, textColor=INK, spaceBefore=9, spaceAfter=3)

small = ParagraphStyle("small", parent=body, fontSize=8, leading=11.4,
                       textColor=INK_SOFT, spaceAfter=4)
cap = ParagraphStyle("cap", parent=small, fontSize=7.6, leading=10.4,
                     textColor=MUTED, alignment=TA_LEFT, spaceBefore=3)

tbl_cell = ParagraphStyle("tbl_cell", parent=ss["Normal"], fontName="Body",
                          fontSize=8, leading=11, textColor=INK)
tbl_cell_s = ParagraphStyle("tbl_cell_s", parent=tbl_cell, fontSize=7.5,
                            leading=10.4, textColor=INK_SOFT)
tbl_head = ParagraphStyle("tbl_head", parent=tbl_cell, fontName="Body-Bold",
                          fontSize=7.4, leading=10, textColor=colors.white)

box_body = ParagraphStyle("box_body", parent=body, fontSize=8.6, leading=12.6,
                          spaceAfter=0)
box_head = ParagraphStyle("box_head", parent=ss["Normal"], fontName="Body-Bold",
                          fontSize=8.6, leading=12, textColor=ACCENT,
                          spaceAfter=4)

# cover
cover_kicker = ParagraphStyle("ck", parent=ss["Normal"], fontName="Body-Bold",
                              fontSize=9, leading=12, textColor=ACCENT_LT,
                              spaceAfter=6)
cover_title = ParagraphStyle("ct", parent=ss["Normal"], fontName="Body-Bold",
                             fontSize=27, leading=32, textColor=INK,
                             spaceAfter=8)
cover_sub = ParagraphStyle("cs", parent=ss["Normal"], fontName="Body",
                           fontSize=12, leading=17.5, textColor=INK_SOFT,
                           spaceAfter=14)


# ---------------------------------------------------------------- helpers
def rule(space_before=2, space_after=8, color=RULE, width=CONTENT_W, thick=0.6):
    t = Table([[""]], colWidths=[width], rowHeights=[0.1])
    t.setStyle(TableStyle([
        ("LINEABOVE", (0, 0), (-1, -1), thick, color),
        ("TOPPADDING", (0, 0), (-1, -1), space_before),
        ("BOTTOMPADDING", (0, 0), (-1, -1), space_after),
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("RIGHTPADDING", (0, 0), (-1, -1), 0),
    ]))
    return t


def section(number, title):
    """Returns flowables for a numbered section heading."""
    return [
        Spacer(1, 6),
        Paragraph(number, h1_num),
        Paragraph(esc(title), h1),
        rule(space_before=3, space_after=9, color=ACCENT_LT, thick=1.1),
    ]


def callout(heading, text, tone="accent"):
    wash, edge = ACCENT_WASH, ACCENT_LT
    if tone == "good":
        wash, edge = GOOD_WASH, GOOD
    elif tone == "warn":
        wash, edge = WARN_WASH, WARN
    head_style = ParagraphStyle("bh", parent=box_head, textColor=edge)
    inner = [Paragraph(heading, head_style), Paragraph(text, box_body)]
    t = Table([[inner]], colWidths=[CONTENT_W])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), wash),
        ("LINEBEFORE", (0, 0), (0, -1), 2.4, edge),
        ("LEFTPADDING", (0, 0), (-1, -1), 10),
        ("RIGHTPADDING", (0, 0), (-1, -1), 10),
        ("TOPPADDING", (0, 0), (-1, -1), 9),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 9),
    ]))
    return KeepTogether([Spacer(1, 3), t, Spacer(1, 9)])


def data_table(header, rows, widths, aligns=None, zebra=True):
    data = [[Paragraph(h, tbl_head) for h in header]]
    for r in rows:
        data.append([c if not isinstance(c, str) else Paragraph(c, tbl_cell)
                     for c in r])
    t = Table(data, colWidths=widths, repeatRows=1)
    style = [
        ("BACKGROUND", (0, 0), (-1, 0), ACCENT),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("LINEBELOW", (0, 0), (-1, -2), 0.4, RULE),
        ("LINEBELOW", (0, -1), (-1, -1), 0.8, RULE),
    ]
    if zebra:
        for i in range(1, len(data)):
            if i % 2 == 0:
                style.append(("BACKGROUND", (0, i), (-1, i), SURFACE))
    if aligns:
        for col, al in aligns.items():
            style.append(("ALIGN", (col, 0), (col, -1), al))
    t.setStyle(TableStyle(style))
    return t


def pill(text, tone):
    fill = {"good": GOOD_WASH, "warn": WARN_WASH, "accent": ACCENT_WASH}[tone]
    ink = {"good": GOOD, "warn": WARN, "accent": ACCENT}[tone]
    st = ParagraphStyle("pill", parent=tbl_cell, fontName="Body-Bold",
                        fontSize=7, leading=9.5, textColor=ink)
    t = Table([[Paragraph(text, st)]])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), fill),
        ("LEFTPADDING", (0, 0), (-1, -1), 5),
        ("RIGHTPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 2.5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 2.5),
        ("BOX", (0, 0), (-1, -1), 0.4, ink),
    ]))
    return t


def qa(qid, question, answer_label, answer):
    """One resolved-question block."""
    idst = ParagraphStyle("idst", parent=tbl_cell, fontName="Body-Bold",
                          fontSize=8.6, leading=12, textColor=ACCENT_LT)
    qst = ParagraphStyle("qst", parent=tbl_cell, fontName="Body-Bold",
                         fontSize=8.8, leading=12.4, textColor=INK)
    ast = ParagraphStyle("ast", parent=body, fontSize=8.4, leading=12.2,
                         spaceAfter=0)
    lbl = ParagraphStyle("lbl", parent=cap, fontName="Body-Bold",
                         textColor=GOOD, fontSize=7.2, spaceBefore=0,
                         spaceAfter=3)
    left = [Paragraph(qid, idst)]
    right = [Paragraph(question, qst), Spacer(1, 4),
             Paragraph(answer_label, lbl), Paragraph(answer, ast)]
    t = Table([[left, right]], colWidths=[16 * mm, CONTENT_W - 16 * mm])
    t.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (0, -1), 0),
        ("RIGHTPADDING", (0, 0), (0, -1), 6),
        ("LEFTPADDING", (1, 0), (1, -1), 10),
        ("RIGHTPADDING", (1, 0), (1, -1), 0),
        ("TOPPADDING", (0, 0), (-1, -1), 8),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
        ("LINEBEFORE", (1, 0), (1, -1), 1.4, RULE),
        ("LINEBELOW", (0, 0), (-1, -1), 0.4, RULE),
    ]))
    return KeepTogether(t)


# ---------------------------------------------------------------- chrome
def page_chrome(canvas, doc):
    canvas.saveState()
    if doc.page > 1:
        canvas.setFont("Body", 7.2)
        canvas.setFillColor(MUTED)
        canvas.drawString(MARGIN, PAGE_H - MARGIN + 7 * mm,
                          "AERF v0.4.1 — Patch Status and v0.4.2 Scoping Report")
        canvas.setStrokeColor(RULE)
        canvas.setLineWidth(0.5)
        canvas.line(MARGIN, PAGE_H - MARGIN + 5.4 * mm,
                    PAGE_W - MARGIN, PAGE_H - MARGIN + 5.4 * mm)
        canvas.setFont("Body", 7.2)
        canvas.setFillColor(MUTED)
        canvas.drawString(MARGIN, MARGIN - 8 * mm, "costinfl/aerf")
        canvas.drawRightString(PAGE_W - MARGIN, MARGIN - 8 * mm, str(doc.page))
    canvas.restoreState()


class Doc(BaseDocTemplate):
    def __init__(self, path):
        super().__init__(path, pagesize=A4,
                         leftMargin=MARGIN, rightMargin=MARGIN,
                         topMargin=MARGIN, bottomMargin=MARGIN,
                         title="AERF v0.4.1 — Patch Status and v0.4.2 Scoping Report",
                         author="costinfl/aerf",
                         subject="What shipped in v0.4.1, how each open question was "
                                 "resolved, and what remains for v0.4.2 / v0.5")
        frame = Frame(MARGIN, MARGIN, CONTENT_W, PAGE_H - 2 * MARGIN, id="main")
        self.addPageTemplates([PageTemplate(id="all", frames=[frame],
                                            onPage=page_chrome)])


# =====================================================================
# CONTENT — everything below this line is the report itself. Everything
# above is layout machinery that carries over unchanged between editions.
# See the module docstring for which data lists to update.
# =====================================================================
S = []

# ============================================================ COVER
cover_band = Table([[""]], colWidths=[CONTENT_W], rowHeights=[3])
cover_band.setStyle(TableStyle([
    ("BACKGROUND", (0, 0), (-1, -1), ACCENT),
    ("LEFTPADDING", (0, 0), (-1, -1), 0),
    ("RIGHTPADDING", (0, 0), (-1, -1), 0),
]))
S += [Spacer(1, 26 * mm), cover_band, Spacer(1, 10)]
S.append(Paragraph("ARCHITECTURAL ENTROPY REDUCTION FRAMEWORK", cover_kicker))
S.append(Paragraph("v0.4.1 — Patch Status<br/>and v0.4.2 Scoping", cover_title))
S.append(Paragraph(
    "What was amended and committed, how each open question was actually "
    "resolved, and what genuinely remains — separated into refinements that "
    "belong in v0.4.2 and new subsystems that belong in v0.5.", cover_sub))

meta_rows = [
    ["Repository", "costinfl/aerf"],
    ["Branch", "claude/aerf-core-domain-model-gwumun"],
    ["Head commit at issue", "cc602de — “Amendment 7 + triage the remaining open-questions register”"],
    ["Spec baseline", "AERF v0.4 — Canonical Pre-Implementation Documentation"],
    ["Patch document", "docs/aerf-v0.4.1-patch.md (7 amendments + 1 non-normative note)"],
    ["Implementation state", "6 Maven modules · 219 tests green · 21 increments · 2 real repositories scanned"],
]
mt = Table([[Paragraph(f"<b>{esc(k)}</b>", tbl_cell_s), Paragraph(esc(v), tbl_cell_s)]
            for k, v in meta_rows], colWidths=[38 * mm, CONTENT_W - 38 * mm])
mt.setStyle(TableStyle([
    ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ("LEFTPADDING", (0, 0), (-1, -1), 0),
    ("RIGHTPADDING", (0, 0), (-1, -1), 6),
    ("TOPPADDING", (0, 0), (-1, -1), 4),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ("LINEBELOW", (0, 0), (-1, -2), 0.4, RULE),
]))
S += [rule(space_before=6, space_after=8), mt]

S.append(Spacer(1, 14))
S.append(callout(
    "Headline for planning",
    "AERF v0.4's own MVP freeze table (§11) is complete, and every open question "
    "that real evidence or a text ambiguity could settle has been settled — "
    "<b>8 of the 22 questions raised across the project are closed</b>. The "
    "14 that remain were not skipped: each depends on a subsystem that was "
    "never commissioned. Five of them are cheap refinements to machinery that "
    "already exists (<b>v0.4.2</b>); nine require new subsystems that were "
    "explicitly outside the MVP from the beginning (<b>v0.5</b>). One of the "
    "five — baseline-relative drift (§5.3) — is no longer blocked at all: "
    "Increment 19 built the storage it was waiting for.", tone="good"))

S.append(Spacer(1, 32 * mm))
S.append(rule(space_before=0, space_after=6, color=ACCENT_LT, thick=1.1))
S.append(Paragraph(
    "Prepared as the planning base for <b>v0.4.2</b>. Sections 5 and 6 carry the "
    "outstanding work and the proposed split; everything before them is the record those "
    "recommendations rest on.", small))

S.append(PageBreak())

# ============================================================ 1. SUMMARY
S += section("SECTION 1", "Executive summary")

S.append(Paragraph(
    "This report covers the state of the AERF reference implementation at commit "
    "<font name='Mono' size='8'>cc602de</font>. It answers three questions: what "
    "the v0.4.1 patch actually contains and what was committed against it; how "
    "each resolved open question was resolved in practice; and what is genuinely "
    "still outstanding, split by whether it belongs to a v0.4.2 refinement pass "
    "or to v0.5.", lede))

S.append(Paragraph("The shape of the work so far", h2))
S.append(Paragraph(
    "v0.4.1 is not a rewrite of v0.4. It is a set of <b>seven numbered amendments</b> "
    "to specific sections of the frozen v0.4 document, each adopted because building "
    "the reference implementation ran into something the specification either did not "
    "represent at all, or left genuinely ambiguous. Every amendment cites the original "
    "text, the problem found, the decision taken, and the increment that produced the "
    "evidence — so the record of <i>why</i> the model changed survives independently "
    "of the code.", body))
S.append(Paragraph(
    "Alongside the patch, an <b>open questions register</b> tracks conceptual questions "
    "that surfaced during implementation but were <i>not</i> resolved. The register's "
    "discipline is that an entry leaves it only when actually settled — not when merely "
    "discussed. That discipline is why the outstanding list below is trustworthy as a "
    "planning input.", body))

stat_rows = [
    ["22", "questions raised", "across 21 increments"],
    ["8", "resolved", "6 via patch amendments, 2 via implementation"],
    ["14", "outstanding", "5 for v0.4.2, 9 for v0.5"],
    ["219", "tests green", "across 6 Maven modules"],
]
cells = []
for big, label, sub in stat_rows:
    bigst = ParagraphStyle("bg", parent=tbl_cell, fontName="Body-Bold",
                           fontSize=19, leading=22, textColor=ACCENT)
    labst = ParagraphStyle("lb", parent=tbl_cell, fontName="Body-Bold",
                           fontSize=8, leading=11, textColor=INK)
    subst = ParagraphStyle("sb", parent=tbl_cell, fontSize=7, leading=9.6,
                           textColor=MUTED)
    cells.append([Paragraph(big, bigst), Paragraph(label, labst),
                  Paragraph(sub, subst)])
stat_tbl = Table([cells], colWidths=[CONTENT_W / 4.0] * 4)
stat_tbl.setStyle(TableStyle([
    ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ("BACKGROUND", (0, 0), (-1, -1), SURFACE),
    ("BOX", (0, 0), (-1, -1), 0.5, RULE),
    ("INNERGRID", (0, 0), (-1, -1), 0.5, RULE),
    ("LEFTPADDING", (0, 0), (-1, -1), 10),
    ("RIGHTPADDING", (0, 0), (-1, -1), 8),
    ("TOPPADDING", (0, 0), (-1, -1), 10),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 10),
]))
S += [Spacer(1, 4), stat_tbl, Spacer(1, 4)]
S.append(Paragraph(
    "Counts as of commit cc602de. “Questions raised” includes four resolved before the "
    "register adopted numbering; the register itself numbers 18, of which 4 are closed.",
    cap))

S.append(Paragraph("What changed most recently", h2))
S.append(Paragraph(
    "Three commits closed out the v0.4.1 line. <b>Increment 20</b> taught the extraction "
    "adapter to recognise Spring Data repositories by marker-interface inheritance rather "
    "than only by <font name='Mono' size='8'>@Repository</font> — the concrete failure "
    "case a real spring-petclinic run had exposed. <b>Increment 21</b> added the structural "
    "<font name='Mono' size='8'>MEMBER_OF</font> relation the canonical model was missing "
    "(Amendment 6), and caught a real measurement bug in the process. The <b>triage pass</b> "
    "resolved the confidence/role-outcome ambiguity (Amendment 7) and re-read every "
    "remaining register entry against the same bar, recording why each stays open.", body))

# ============================================================ 2. AMENDMENTS
S.append(PageBreak())
S += section("SECTION 2", "What v0.4.1 contains")

S.append(Paragraph(
    "Seven amendments, plus one non-normative implementation note. Six of the seven "
    "closed a question that had been formally recorded as open; Amendment 5 came from "
    "a planning decision rather than a register entry. Three amendments required no "
    "code at all — they formalise an interpretation the implementation had already "
    "adopted without ever stating it.", lede))

amend_rows = [
    ["1", "Evidence gains an execution-context field",
     "§2.2, §2.4,\nApp. A", "6", "Yes"],
    ["2", "Layer-entropy matrix must be declared explicitly, not derived from an ordering",
     "§4.1", "3", "No"],
    ["3", "“Evidence-weighted” N+1 score is defined concretely",
     "§4.3, App. B", "6", "Yes"],
    ["4", "Iterative role refinement is scoped to monotonic transitions",
     "§3.2", "4", "Yes"],
    ["5", "Evidence gains structured attributes",
     "§2.2, §2.4,\nApp. A", "11", "Yes"],
    ["6", "Edge relation set gains a structural MEMBER_OF relation",
     "§2.4", "21", "Yes"],
    ["7", "Confidence is unaffected by a node's role outcome",
     "§5.4", "21", "No"],
]
rows = []
for num, title, sect, inc, code in amend_rows:
    numst = ParagraphStyle("an", parent=tbl_cell, fontName="Body-Bold",
                           fontSize=9.5, textColor=ACCENT_LT)
    rows.append([
        Paragraph(num, numst),
        Paragraph(f"<b>{esc(title)}</b>", tbl_cell),
        Paragraph(esc(sect).replace("\n", "<br/>"), tbl_cell_s),
        Paragraph(inc, tbl_cell_s),
        Paragraph(code, tbl_cell_s),
    ])
S.append(data_table(
    ["", "Amendment", "Affects", "Incr.", "Code?"],
    rows, [10 * mm, CONTENT_W - 58 * mm, 22 * mm, 12 * mm, 14 * mm],
    aligns={0: "CENTER", 3: "CENTER", 4: "CENTER"}))
S.append(Paragraph(
    "“Code?” distinguishes an amendment that changed the implementation from one that "
    "formalises an interpretation already in force. Amendments 2 and 7 changed no code "
    "by design — a reader of v0.4 alone would not have known the restriction existed.",
    cap))

S.append(Paragraph("The two amendments a reader should not skip", h2))

S.append(callout(
    "Amendment 2 — the ordering is illustrative, not a derivation recipe",
    "§4.1 offers a “typical allowed ordering” (Presentation → Application → Domain → "
    "Persistence → Infrastructure) next to a requirement that the relation matrix stay "
    "governance-configurable. Read together, that invites deriving the matrix from the "
    "ordering. Two natural derivations were tried and <b>both contradict a case v0.4 "
    "treats as settled elsewhere</b>: adjacent-layers-only flags Application → Persistence, "
    "an unremarkable pattern; any-forward-call fails to flag Presentation → Persistence, "
    "which §6.3's own worked invariant calls a critical violation. Since no mechanical "
    "rule matches the framework's own example, the matrix must always be declared "
    "explicitly. No future implementation should reintroduce a derivation silently."))

S.append(callout(
    "Amendment 4 — termination is structural, not asserted",
    "§3.2 states that inference “terminates when a fixed point is reached”, but establishes "
    "no such guarantee for an arbitrary refinement function F. Two rules that each flip a "
    "node's role in response to the other would oscillate forever. v0.4.1 scopes F to rules "
    "that may only fire on an <font name='Mono' size='8'>Unknown</font> node and may only "
    "return a concrete role. The count of Unknown nodes then strictly decreases on any pass "
    "that changes anything, over a finite node set — so termination within |V|+1 passes is "
    "guaranteed by structure. The more general revising form of F is <i>deferred, not ruled "
    "out</i>; it is register entry #5, and needs its own convergence argument."))

S.append(Paragraph("Amendment 6, and the measurement bug it exposed", h2))
S.append(Paragraph(
    "Adding <font name='Mono' size='8'>MEMBER_OF</font> — a purely structural relation "
    "from a FUNCTION node to the COMPONENT that declares it — looked inert. It was not. "
    "A <font name='Mono' size='8'>MEMBER_OF</font> edge's target is, by construction, "
    "always resolvable: a method's declaring class is never external. "
    "<font name='Mono' size='8'>AnalysisConfidence</font> (§5.4) is deliberately "
    "graph-wide and counted <i>every</i> edge, so these guaranteed-resolved edges "
    "inflated confidence on the pipeline's own defect sample from "
    "<b>0.714 to 0.857</b> — a movement caused entirely by the sample having methods, "
    "with no relationship to how well extraction resolved anything genuinely uncertain. "
    "Caught immediately by an existing exact-value assertion. Confidence now excludes "
    "the relation from both numerator and denominator, and a test proves the exclusion "
    "is unconditional rather than merely moot.", body))
S.append(Paragraph(
    "The wider lesson, worth carrying into v0.4.2: <b>any new relation type is a "
    "measurement change, not just a modelling change</b>. The three entropy calculators "
    "were unaffected only because each already scopes itself to an explicit relation "
    "allow-list. Confidence had no such filter, and needed one.", body))

# ============================================================ 3. IMPLEMENTATION
S.append(Spacer(1, 8))
S += section("SECTION 3", "What was committed")

S.append(Paragraph(
    "Twenty-one increments, each one commit, each with its own document in "
    "<font name='Mono' size='8'>docs/</font>. Increments 1–10 completed v0.4 §11's MVP "
    "freeze table. Increments 11–16 built the extraction adapter under a standing plan. "
    "Increments 17–21 hardened it, ran it against real code, published the results, and "
    "closed out the remaining answerable questions.", lede))

inc_groups = [
    ("Increments 1–10 · the MVP freeze table (§11)", [
        ("1", "AERF core domain model"),
        ("2", "Seed role inference"),
        ("3", "Layer entropy"),
        ("4", "Iterative graph-relationship role refinement"),
        ("5", "Cycle entropy"),
        ("6", "Basic persistence / N+1 heuristic"),
        ("7", "Basic security entropy"),
        ("8", "Calibration model — aggregation, maturity, confidence"),
        ("9", "Invariant DSL core"),
        ("10", "JSON evidence / reporting"),
    ]),
    ("Increments 11–16 · the ExtractionAdapter Plan", [
        ("11", "Evidence structured attributes (Amendment 5)"),
        ("12", "The extraction bridge — aerf-extraction module"),
        ("13", "aerf-openrewrite, class-level extraction"),
        ("14", "Method-level extraction and execution context"),
        ("15", "Spring / annotation semantic evidence"),
        ("16", "Pipeline runner and CI — plan complete"),
    ]),
    ("Increments 17–21 · hardening, real code, closing out", [
        ("17", "Iterative StronglyConnectedComponents (closed #7)"),
        ("18", "A real-repository pipeline run (exposed #1's failure case)"),
        ("19", "Scan dashboard, published to GitHub Pages"),
        ("20", "Spring Data marker-interface evidence (closed #18)"),
        ("21", "Structural MEMBER_OF relation (closed #17, Amendment 6)"),
    ]),
]
for gtitle, items in inc_groups:
    S.append(Paragraph(esc(gtitle), h3))
    rws = []
    for num, title in items:
        numst = ParagraphStyle("in", parent=tbl_cell, fontName="Body-Bold",
                               fontSize=8, textColor=ACCENT_LT)
        rws.append([Paragraph(num, numst), Paragraph(esc(title), tbl_cell)])
    t = Table(rws, colWidths=[10 * mm, CONTENT_W - 10 * mm])
    t.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("ALIGN", (0, 0), (0, -1), "RIGHT"),
        ("LEFTPADDING", (0, 0), (0, -1), 0),
        ("RIGHTPADDING", (0, 0), (0, -1), 8),
        ("LEFTPADDING", (1, 0), (1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
        ("LINEBEFORE", (1, 0), (1, -1), 0.8, RULE),
    ]))
    S += [t, Spacer(1, 6)]

S.append(Paragraph("Verification state", h2))
mod_rows = [
    ["aerf-model", "Canonical domain model", "32"],
    ["aerf-analysis", "Role inference, entropy metrics, invariants, calibration", "117"],
    ["aerf-report", "Evidence and report serialization", "24"],
    ["aerf-extraction", "Technology-agnostic extraction bridge", "23"],
    ["aerf-openrewrite", "OpenRewrite adapter — the only module carrying it", "17"],
    ["aerf-pipeline", "End-to-end runner", "6"],
]
rows = [[Paragraph(f"<font name='Mono' size='7.6'>{esc(m)}</font>", tbl_cell),
         Paragraph(esc(d), tbl_cell_s),
         Paragraph(f"<b>{t}</b>", tbl_cell)] for m, d, t in mod_rows]
rows.append([Paragraph("<b>Total</b>", tbl_cell), Paragraph("", tbl_cell),
             Paragraph("<b>219</b>", tbl_cell)])
S.append(data_table(["Module", "Responsibility", "Tests"], rows,
                    [34 * mm, CONTENT_W - 52 * mm, 18 * mm],
                    aligns={2: "RIGHT"}))

S.append(Spacer(1, 6))
S.append(Paragraph(
    "The §9 module boundary — “the AERF graph must not expose OpenRewrite AST types” — "
    "is enforced mechanically rather than by convention: "
    "<font name='Mono' size='8'>maven-enforcer-plugin</font> bans "
    "<font name='Mono' size='8'>org.openrewrite:*</font> from every module except the "
    "adapter, so a violation fails the build.", body))
S.append(Paragraph(
    "Two real repositories have been scanned end-to-end and their reports published to "
    "the live dashboard: <b>spring-petclinic</b> (modern Spring Boot) and "
    "<b>spring-framework-petclinic</b> (pre-Boot, JSP-era). The second produced the "
    "project's first genuine persistence-entropy finding — a real N+1 in "
    "<font name='Mono' size='7.6'>JdbcOwnerRepositoryImpl#loadOwnersPetsAndVisits</font>.",
    body))

# ============================================================ 4. RESOLVED
S.append(Spacer(1, 8))
S += section("SECTION 4", "How the resolved questions were resolved")

S.append(Paragraph(
    "Eight questions are closed. What follows is how each was actually settled — in "
    "several cases not the way the question itself predicted, which is the more useful "
    "part of the record.", lede))

S.append(qa("A1",
            "How is “repeated execution context” represented at all?",
            "RESOLVED — Amendment 1, Increment 6",
            "§4.3's N+1 heuristic depends on knowing whether a persistence access happens "
            "inside a loop; §2's frozen model had no field for it. Evidence gained an "
            "<font name='Mono' size='7.6'>execution_context</font> field with three values "
            "— SINGLE, ITERATED, UNKNOWN — defaulting to UNKNOWN specifically so every piece "
            "of evidence recorded in Increments 1–5 keeps its original meaning exactly. A "
            "general control-flow graph concept was considered and rejected as "
            "disproportionate to what §11 itself frames as a “basic” heuristic."))

S.append(qa("A2",
            "Does the layer-entropy matrix derive from the stated ordering?",
            "RESOLVED — Amendment 2, Increment 3",
            "No. Both natural derivations contradict cases v0.4 settles elsewhere (detail in "
            "Section 2). The matrix must always be declared explicitly; v0.4.1 defines no "
            "default derivation."))

S.append(qa("A3",
            "What does “evidence-weighted” mean for the N+1 score?",
            "RESOLVED — Amendment 3",
            "Appendix B gave the formula as “evidence-weighted N+1 patterns / relevant "
            "persistence contexts” while defining “evidence-weighted” nowhere. The weight of "
            "one relevant persistence context is now the count of its evidence items marked "
            "ITERATED — the number of independently observed repeated-execution call sites "
            "backing that access. Reported <i>alongside</i> the plain flagged/relevant ratio, "
            "not instead of it, per §14's measurement-before-aggregation principle."))

S.append(qa("A4",
            "Is iterative role refinement's F guaranteed to terminate?",
            "RESOLVED — Amendment 4, Increment 4",
            "Not for arbitrary F. Scoped to monotonic transitions, termination within |V|+1 "
            "passes becomes structural (detail in Section 2). The revising form remains open "
            "as register entry #5."))

S.append(qa("#7",
            "Will the recursive Tarjan implementation overflow the stack on real graphs?",
            "RESOLVED — Increment 17, implementation debt (no spec change)",
            "Yes, and it was fixed before it could bite. "
            "<font name='Mono' size='7.6'>StronglyConnectedComponents</font> was rewritten "
            "iteratively and verified against a 200,000-node chain that reliably "
            "<font name='Mono' size='7.6'>StackOverflowError</font>s the previous recursive "
            "version on a default JVM stack. This was the explicit gate on running the "
            "pipeline against a real repository — Increment 18 followed immediately."))

S.append(qa("#3",
            "How should per-node Unknown and graph-wide confidence (§5.4) interact?",
            "RESOLVED — Amendment 7, Increment 21 triage (no code change)",
            "They don't. Confidence asks whether the <i>graph</i> resolved an edge's endpoints "
            "— an extraction-stage question — and never inspects a node's Role, a "
            "role-inference-stage outcome. A node can be fully resolved structurally and still "
            "be Unknown by role; conflating the two would hide which stage actually degraded. "
            "The amendment formalises what "
            "<font name='Mono' size='7.6'>AnalysisConfidence</font> had implemented since "
            "Increment 8 without ever stating it."))

S.append(qa("#17",
            "Should the model have a structural FUNCTION → declaring COMPONENT relation?",
            "RESOLVED — Amendment 6, Increment 21",
            "Yes: <font name='Mono' size='7.6'>MEMBER_OF</font>. The question offered three "
            "options; the first and third were adopted together and the second was rejected "
            "for a concrete reason found while evaluating it. A refinement rule consuming the "
            "relation could only resolve a method one full pass <i>after</i> its class resolves, "
            "because rules see roles only as of the start of a pass (Amendment 4). That would "
            "shift pass counts several already-verified worked examples depend on, for no gain "
            "over the evidence-time propagation that already works. The relation was added for "
            "its own sake; role inference was left alone."))

S.append(qa("#18",
            "Should Spring Data repositories be recognised by marker interface, not just @Repository?",
            "RESOLVED — Increment 20, adapter-level (no spec change)",
            "Yes — but <b>not</b> where the question assumed. It proposed a new seed rule; that "
            "was impossible. <font name='Mono' size='7.6'>RoleInferenceRule</font> is "
            "contractually a pure function of one node's own evidence with no graph access, so "
            "a rule matching an external, unextracted supertype by FQN could never have been "
            "written as a seed rule at all. The adapter recognises the marker interface and "
            "emits ordinary spring-data evidence instead — the existing seed rule needed "
            "<b>no change whatsoever</b>. Scoped to directly declared supertypes, not "
            "transitively; no real repository has yet contradicted that choice."))

S.append(Spacer(1, 8))
S.append(callout(
    "Partially answered, deliberately still open — #1",
    "“When does seed-only role classification actually fail?” got its first real answer in "
    "Increment 18: every repository interface in a real spring-petclinic checkout came back "
    "<font name='Mono' size='8'>UNKNOWN</font>, because idiomatic Spring Data carries no "
    "annotation. Increment 20 fixed that specific shape. The entry stays open because closing "
    "one named failure case does not establish there are no others — and no case has yet been "
    "found where seed and graph refinement <i>together</i> get a wrong answer, as opposed to "
    "seed alone missing evidence an adapter had not been taught to produce.", tone="warn"))

# ============================================================ 5. OUTSTANDING
S.append(Spacer(1, 8))
S += section("SECTION 5", "What remains outstanding")

S.append(Paragraph(
    "Fourteen entries. Every one was re-read in the Increment 21 triage against the same bar "
    "the resolved questions met, and left open deliberately. The reason is consistent: each "
    "depends on something that does not exist yet and was never commissioned. Deciding them "
    "now would mean designing a speculative subsystem against nothing.", lede))

S.append(Paragraph("Grouped by what each is actually blocked on", h2))

block_rows = [
    ("Governance configuration format\ndoes not exist", "#2, #4, #6",
     "Governance evidence class (§3.3's fourth class); per-subsystem layer matrices; "
     "per-module cycle-entropy scoping. All three are the same shape: a policy an "
     "organisation declares, with no format yet to declare it in."),
    ("Governance-facing risk view\nis undesigned", "#9, #14, #15, #16",
     "How an approved exception suppresses a persistence finding; how §5.3's R combines "
     "with invariant violations; how E<sub>inv</sub> aggregates its indicators; the unified "
     "view all three imply. §6 gives no combination formula at all, and the constraint that "
     "entropy, drift and violations stay separately visible must survive whatever is chosen."),
    ("Needs a convergence argument", "#5",
     "Non-monotonic (revising) role refinement — explicitly deferred, not ruled out, by "
     "Amendment 4. Needs a bound on how many times a single node may be revised before it "
     "can be attempted at all."),
    ("Needs a new rule shape", "#10",
     "CSRF and mass-assignment are relationship concerns, unlike the implemented XSS rule, "
     "which is a single-node check. Needs either an edge-aware security-rule variant or a "
     "documented node-evidence convention for binding/form configuration."),
    ("Blocked only on a decision\nsomeone has to want", "#1, #8, #11, #12, #13",
     "These five need no new subsystem — see Section 6. Two of them are now unblocked by "
     "work that has already shipped."),
]
rows = []
for blocker, ids, detail in block_rows:
    bst = ParagraphStyle("bs", parent=tbl_cell, fontName="Body-Bold", fontSize=8,
                         leading=11, textColor=INK)
    ist = ParagraphStyle("is", parent=tbl_cell, fontName="Body-Bold", fontSize=8,
                         leading=11, textColor=ACCENT_LT)
    rows.append([
        Paragraph(blocker.replace("\n", "<br/>"), bst),
        Paragraph(ids, ist),
        Paragraph(detail, tbl_cell_s),
    ])
S.append(data_table(["Blocked on", "Entries", "What it covers"], rows,
                    [38 * mm, 22 * mm, CONTENT_W - 60 * mm]))

S.append(PageBreak())
S.append(Paragraph("The full outstanding list", h2))

out_rows = [
    ("1", "When does seed-only role classification actually fail?", "2", "0.4.2"),
    ("2", "Rule authorship boundary for the “Governance” evidence class", "2", "0.5"),
    ("4", "Should LayerPolicy support per-subsystem matrices?", "3", "0.5"),
    ("5", "Non-monotonic (revising) role refinement", "4", "0.5"),
    ("6", "Should cycle entropy be scoped below the whole graph?", "5", "0.5"),
    ("8", "Should the N+1 heuristic constrain the source side too?", "6", "0.4.2"),
    ("9", "How does an approved exception suppress a persistence finding?", "6", "0.5"),
    ("10", "How should CSRF and mass-assignment be modelled?", "7", "0.5"),
    ("11", "Should security “concern” become a closed vocabulary?", "7", "0.4.2"),
    ("12", "How should baseline-relative drift represent and load a baseline?", "8", "0.4.2"),
    ("13", "Should confidence be graph-wide only, or per-dimension too?", "8", "0.4.2"),
    ("14", "How does the drift-aware risk model R combine with violations?", "8", "0.5"),
    ("15", "How should E<sub>inv</sub> aggregate invariant indicators?", "9", "0.5"),
    ("16", "How do violations, E<sub>total</sub> and drift form one governance view?", "9", "0.5"),
]
rows = []
for qid, title, origin, target in out_rows:
    idst = ParagraphStyle("oid", parent=tbl_cell, fontName="Body-Bold", fontSize=8,
                          textColor=ACCENT_LT)
    tone = "good" if target == "0.4.2" else "accent"
    rows.append([
        Paragraph(qid, idst),
        Paragraph(title, tbl_cell),
        Paragraph(origin, tbl_cell_s),
        pill(target, tone),
    ])
S.append(data_table(["#", "Question", "From incr.", "Proposed"], rows,
                    [9 * mm, CONTENT_W - 52 * mm, 20 * mm, 23 * mm],
                    aligns={0: "CENTER", 2: "CENTER"}))

# ============================================================ 6. SPLIT
S.append(PageBreak())
S += section("SECTION 6", "Proposed split — v0.4.2 versus v0.5")

S.append(Paragraph(
    "The dividing line that matches both the project's history and the stated intent for "
    "v0.5 is <b>whether an entry needs a subsystem that was never part of the MVP</b>. Five "
    "entries do not: each is a refinement to machinery that already exists and already has "
    "tests. Nine do, and they cluster into three coherent subsystems rather than nine "
    "independent pieces of work — which makes v0.5 a much smaller-sounding project than a "
    "list of nine questions suggests.", lede))

S.append(Paragraph("v0.4.2 — five refinements, no new subsystems", h2))

v442 = [
    ("#12", "Baseline-relative drift (§5.3)", "high",
     "Deferred in Increment 8 “until JSON/reporting gives it somewhere to live”. That place "
     "now exists and was built for a different reason: Increment 19's Supabase "
     "<font name='Mono' size='7.6'>scans</font> table already stores every historical scan "
     "with all its metrics and the full report JSON. Δ<sub>d</sub> = E<sub>d</sub><super>t"
     "</super> − E<sub>d</sub><super>0</super> is a subtraction once a prior measurement "
     "can be loaded — and the dashboard would show drift per project immediately. "
     "<b>The blocker on this entry has already been removed; nobody noticed.</b>"),
    ("#13", "Per-dimension confidence (§5.4)", "high",
     "Distinct from the now-resolved #3. Each entropy calculator already carries its own "
     "<font name='Mono' size='7.6'>relevantRelations</font> subset, so “persistence entropy "
     "computed at 90% confidence” is the existing computation restricted to an existing "
     "filter. Cost is mostly plumbing: a field per metric in the report, the JSON schema and "
     "the dashboard. Amendment 7's reasoning about which stage a number describes applies "
     "directly."),
    ("#8", "N+1 source-side constraint (§4.3)", "medium",
     "Left open in Increment 6 pending “real evidence about whether the simpler version over- "
     "or under-flags in practice”. That evidence now exists: two real repository runs, "
     "including one genuine N+1 finding in the legacy PetClinic. The question can finally be "
     "decided against data rather than intuition."),
    ("#11", "Security concern vocabulary (§4.4)", "low",
     "Currently free-text by deliberate design. Documented string constants — not necessarily "
     "an enum — would let an invariant reference a concern reliably. Cheap, and worth doing "
     "before invariants start depending on the strings informally."),
    ("#1", "When does seed-only classification fail?", "ongoing",
     "Not a build task: an empirical question that each new real-repository scan answers a "
     "little more. The dashboard already collects the evidence. Worth keeping open and "
     "revisiting rather than scheduling."),
]
for qid, title, prio, detail in v442:
    ptone = {"high": "good", "medium": "accent", "low": "accent",
             "ongoing": "accent"}[prio]
    idst = ParagraphStyle("v4id", parent=tbl_cell, fontName="Body-Bold",
                          fontSize=9, textColor=ACCENT_LT)
    tst = ParagraphStyle("v4t", parent=tbl_cell, fontName="Body-Bold",
                         fontSize=9.2, leading=12.6, textColor=INK)
    head = Table([[Paragraph(qid, idst), Paragraph(esc(title), tst),
                   pill(prio.upper(), ptone)]],
                 colWidths=[11 * mm, CONTENT_W - 40 * mm, 29 * mm])
    head.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("ALIGN", (2, 0), (2, 0), "RIGHT"),
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("RIGHTPADDING", (0, 0), (-1, -1), 0),
        ("TOPPADDING", (0, 0), (-1, -1), 0),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]))
    blk = Table([[[head, Paragraph(detail, ParagraphStyle(
        "v4d", parent=body, fontSize=8.4, leading=12.2, spaceAfter=0))]]],
        colWidths=[CONTENT_W])
    blk.setStyle(TableStyle([
        ("LINEBEFORE", (0, 0), (0, -1), 2.0, RULE),
        ("LEFTPADDING", (0, 0), (-1, -1), 9),
        ("RIGHTPADDING", (0, 0), (-1, -1), 0),
        ("TOPPADDING", (0, 0), (-1, -1), 7),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
    ]))
    S.append(KeepTogether([blk, Spacer(1, 5)]))

S.append(Spacer(1, 6))
S.append(callout(
    "If v0.4.2 does only one thing, make it #12",
    "Drift is the one metric family v0.4 specifies (§5.3) that the implementation has never "
    "produced a single number for — not because it is hard, but because in Increment 8 there "
    "was nowhere to keep a baseline. Increment 19 built exactly that store, for the dashboard, "
    "without the two being connected. Wiring them together turns a static per-scan report into "
    "the trend view the framework was always aiming at, and it unblocks the R side of #14 for "
    "v0.5 as a side effect.", tone="good"))

S.append(PageBreak())
S.append(Paragraph("v0.5 — three subsystems, not nine questions", h2))
S.append(Paragraph(
    "The nine remaining entries are not nine projects. They resolve into three subsystems "
    "plus two independents, and the dependency order between them is fairly strict.", body))

v5 = [
    ("A", "Governance configuration", "#2, #4, #6  (+ enables #9, #11)",
     "The largest and the one everything else waits on. §3.3 names Governance as one of four "
     "evidence classes and nothing represents it; §4.1 requires the layer matrix to be "
     "governance-declared; §4.2's global cycle scope wants per-module policy. One format, "
     "declared once, serves all three — and gives #9's exception model and #11's concern "
     "vocabulary somewhere to live. <b>Do this first.</b>"),
    ("B", "Governance-facing risk view", "#14, #15, #16  (+ #9)",
     "How E<sub>total</sub>, drift and invariant violations are presented together without "
     "collapsing into a single “architecture score” — a constraint the project has held "
     "throughout. Needs §6.1's λ<sub>k</sub> aggregation decided (#15) and R's invariant "
     "term defined (#14). "
     "Depends on A for policy input and benefits from v0.4.2's #12 for the drift term."),
    ("C", "Extended security modelling", "#10",
     "CSRF and mass-assignment need an edge-aware security rule, parallel to "
     "<font name='Mono' size='7.6'>GraphRoleRefinementRule</font>, or a node-evidence "
     "convention for binding configuration. Independent of A and B; can run in parallel."),
    ("D", "Non-monotonic role refinement", "#5",
     "Independent, and gated on a convergence argument rather than on any other subsystem. "
     "Worth attempting only if a real codebase produces a case where a weakly-seeded node "
     "surrounded by strongly-classified neighbours is genuinely misclassified — which #1's "
     "ongoing evidence collection would surface."),
]
for letter, title, ids, detail in v5:
    lst = ParagraphStyle("v5l", parent=tbl_cell, fontName="Body-Bold",
                         fontSize=15, leading=18, textColor=colors.white)
    badge = Table([[Paragraph(letter, lst)]], colWidths=[9 * mm], rowHeights=[9 * mm])
    badge.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), ACCENT),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("RIGHTPADDING", (0, 0), (-1, -1), 0),
        ("TOPPADDING", (0, 0), (-1, -1), 0),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
    ]))
    tst = ParagraphStyle("v5t", parent=tbl_cell, fontName="Body-Bold",
                         fontSize=10, leading=13, textColor=INK)
    ist = ParagraphStyle("v5i", parent=tbl_cell, fontName="Body-Bold",
                         fontSize=7.6, leading=10.4, textColor=ACCENT_LT)
    right = [Paragraph(esc(title), tst), Paragraph(esc(ids), ist), Spacer(1, 3),
             Paragraph(detail, ParagraphStyle("v5d", parent=body, fontSize=8.4,
                                              leading=12.2, spaceAfter=0))]
    t = Table([[badge, right]], colWidths=[9 * mm, CONTENT_W - 9 * mm])
    t.setStyle(TableStyle([
        ("VALIGN", (0, 0), (0, 0), "TOP"),
        ("VALIGN", (1, 0), (1, 0), "TOP"),
        ("LEFTPADDING", (0, 0), (0, -1), 0),
        ("RIGHTPADDING", (0, 0), (0, -1), 0),
        ("LEFTPADDING", (1, 0), (1, -1), 9),
        ("RIGHTPADDING", (1, 0), (1, -1), 0),
        ("TOPPADDING", (0, 0), (-1, -1), 8),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
        ("LINEBELOW", (0, 0), (-1, -1), 0.5, RULE),
    ]))
    S.append(KeepTogether([t, Spacer(1, 3)]))

S.append(Spacer(1, 10))
S.append(Paragraph("Suggested order", h3))
S.append(Paragraph(
    "<b>v0.4.2:</b> #12 (drift, wire the existing store) → #13 (per-dimension confidence) → "
    "#8 (N+1 source side, now decidable against real data) → #11 (concern constants). "
    "#1 continues in the background with each new scan.<br/><br/>"
    "<b>v0.5:</b> A (governance configuration) first, since B and half of A's own siblings "
    "depend on it → B (risk view) once A and v0.4.2's drift work land → C and D in parallel, "
    "neither blocking anything else.", body))

S.append(Spacer(1, 6))
S.append(callout(
    "One caution carried forward from Amendment 6",
    "Every one of these adds either a relation type, a metric, or a policy input. Amendment 6 "
    "showed that adding a relation silently moved a calibration number that had nothing to do "
    "with it, because one metric had no relevance filter while three others did. Before "
    "landing anything in v0.4.2 or v0.5, check what it changes in "
    "<font name='Mono' size='8'>AnalysisConfidence</font> and in each calculator's "
    "<font name='Mono' size='8'>relevantRelations</font> — the exact-value assertions in the "
    "pipeline tests are what caught it last time, and they are worth keeping exact.",
    tone="warn"))

# ============================================================ APPENDIX
S.append(PageBreak())
S += section("APPENDIX A", "Commit trail")

S.append(Paragraph(
    "The commits that carry the v0.4.1 patch and the increments referenced above, newest "
    "first. Every increment is one commit with its own document under "
    "<font name='Mono' size='8'>docs/</font>.", lede))

commits = [
    ("cc602de", "Amendment 7 + triage the remaining open-questions register", "patch"),
    ("42fcded", "Increment 21: structural MEMBER_OF relation (Amendment 6)", "patch"),
    ("129e40d", "Increment 20: recognize Spring Data repositories by marker interface", ""),
    ("13ebb48", "Add a second real scan: spring-framework-petclinic (pre-Boot, JSP-era)", ""),
    ("3c4114a", "Add info-icon tooltips explaining each metric on the dashboard", ""),
    ("2415564", "Fix CI/Pages workflows: this repo has no main branch", ""),
    ("6fd21a3", "Increment 19: scan dashboard, published to GitHub Pages", ""),
    ("a64819e", "Increment 18: run the pipeline against a real cloned repository", ""),
    ("6456c93", "Increment 17: rewrite StronglyConnectedComponents iteratively", ""),
    ("fc07d44", "Increment 16: pipeline runner and CI — ExtractionAdapter Plan complete", ""),
    ("9051424", "Increment 15: Spring/annotation semantic evidence", ""),
    ("ca4a245", "Increment 14: method-level extraction and execution context", ""),
    ("953fedd", "Increment 13: aerf-openrewrite class-level extraction", ""),
    ("88da46a", "Add the extraction bridge: aerf-extraction module (Increment 12)", ""),
    ("77047ec", "Add Evidence structured attributes (Increment 11)", "patch"),
    ("a3f4d5a", "Add ExtractionAdapter Plan for Increments 11–16", ""),
    ("(various)", "Increments 1–10 — one commit each, see docs/increment-01 … increment-10", ""),
    ("a67ff19", "Adopt AERF v0.4.1 patch: formalize amendments (1–4)", "patch"),
]
rows = []
for sha, msg, tag in commits:
    rows.append([
        Paragraph(f"<font name='Mono' size='7.4'>{esc(sha)}</font>", tbl_cell),
        Paragraph(esc(msg), tbl_cell_s),
        pill("PATCH", "accent") if tag else Paragraph("", tbl_cell_s),
    ])
S.append(data_table(["Commit", "Message", ""], rows,
                    [22 * mm, CONTENT_W - 22 * mm - 20 * mm, 20 * mm]))
S.append(Paragraph(
    "“PATCH” marks a commit that added or changed an amendment in "
    "docs/aerf-v0.4.1-patch.md. All commits are on branch "
    "claude/aerf-core-domain-model-gwumun, which is also this repository's default branch.",
    cap))

S.append(Spacer(1, 8))
S.append(Paragraph("Source documents in the repository", h3))
src_rows = [
    ["docs/aerf-v0.4.1-patch.md", "The seven amendments, each with original text, problem, decision, evidence"],
    ["docs/open-questions-register.md", "Living register — outstanding entries plus the resolved list, with the Increment 21 triage note"],
    ["docs/extraction-adapter-plan.md", "The standing plan covering Increments 11–16, amended by appending"],
    ["docs/increment-NN-*.md", "One document per increment, 21 in total"],
]
rows = [[Paragraph(f"<font name='Mono' size='7.4'>{esc(p)}</font>", tbl_cell),
         Paragraph(esc(d), tbl_cell_s)] for p, d in src_rows]
S.append(data_table(["Path", "Contents"], rows,
                    [50 * mm, CONTENT_W - 50 * mm]))

S.append(Spacer(1, 7))
S.append(rule(space_before=0, space_after=6))
S.append(Paragraph(
    "Report generated from the repository state at commit cc602de. Every figure in this "
    "document — test counts, confidence values, question counts — is taken from the committed "
    "documents and a full <font name='Mono' size='7.4'>mvn -B test</font> run, not from "
    "recollection.", cap))

if __name__ == "__main__":
    default_out = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "aerf-v0.4.1-status-report.pdf")
    out_path = sys.argv[1] if len(sys.argv) > 1 else default_out
    Doc(out_path).build(S)
    print(f"built {out_path}")
