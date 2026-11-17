---
name: UniLink
description: >
  Design system for UniLink, a verified university-student networking platform.
  Derived from pic.docx (a dark Academic Overview screen and a light Account
  settings modal). Every colour below was sampled from the pixels of those two
  screenshots. Values marked "approx" were measured from the screenshots and are
  not authoritative; values marked "UNCONFIRMED" were not in the reference.
version: alpha
# ---------------------------------------------------------------------------
# LIGHT THEME (sampled from the Account modal screenshot).
# Unprefixed colour tokens are the LIGHT theme. Tokens prefixed "dark-" are the
# DARK theme (the CLI ignores unknown top-level keys on export, so both themes
# live inside the recognised `colors` map). Each light token has a dark twin.
# ---------------------------------------------------------------------------
colors:
  background: "#fdfdfb"          # page / modal background
  surface: "#ffffff"             # raised surface: cards, inputs, pills, right pane
  surface-muted: "#f2f1ed"       # selected nav row, hover fill
  border: "#e4e2dc"              # 1px hairlines and dividers
  text-primary: "#14140f"        # headings, primary labels
  text-secondary: "#5b5951"      # secondary labels, descriptions
  text-muted: "#8b8b8b"          # captions, placeholders, eyebrow text
  # Not present in the reference as exact values (see Colors > Gaps):
  # accent, danger, warning, success, focus ring, scrim.
  # They are intentionally NOT invented here.
  on-primary: "#fdfdfb"
  primary: "#14140f"             # primary action = ink on paper (no brand hue in reference)
  scrim: "#a8a8a5"               # dimmed page seen behind the modal (approx, includes blur)
  # ---- DARK THEME (sampled from the Academic Overview screenshot) ----
  # Same semantics as the light tokens above, prefixed "dark-". At build time
  # these map onto the same CSS custom properties under [data-theme="dark"].
  dark-background: "#0e0e0c"          # page background
  dark-surface: "#171715"             # raised surface: pills, avatar chip, logo tile
  dark-surface-muted: "#22221e"       # hover fill (approx, least frequent sampled shade)
  dark-border: "#262622"              # 1px hairlines, header rule, stat strip dividers
  dark-text-primary: "#f5f4f0"        # headings, primary labels
  dark-text-secondary: "#a9a69c"      # lead paragraph, secondary labels
  dark-text-muted: "#726f67"          # eyebrow, stat captions, timestamps
  dark-on-primary: "#0e0e0c"
  dark-primary: "#f5f4f0"             # primary action = paper on ink

typography:
  # fontFamily is UNCONFIRMED: the screenshots show a clean grotesque sans-serif
  # but the exact family cannot be read from an image. Replace once confirmed.
  # Bangla fallback (Noto Sans Bengali) is an engineering requirement from
  # NFR-LOC-01, not a value from pic.docx.
  display:
    fontFamily: system-ui
    fontSize: 2.5rem             # approx: heading cap-height ~31px at screenshot scale
    fontWeight: 500
    lineHeight: 1.1
    letterSpacing: "-0.02em"
  headline:
    fontFamily: system-ui
    fontSize: 1.5rem             # section titles such as "Today", "Coming up"
    fontWeight: 400
    lineHeight: 1.25
  title:
    fontFamily: system-ui
    fontSize: 1.125rem           # modal title "Account"
    fontWeight: 500
    lineHeight: 1.3
  body-lg:
    fontFamily: system-ui
    fontSize: 1.125rem           # lead paragraph under the page heading
    fontWeight: 300
    lineHeight: 1.5
  body-md:
    fontFamily: system-ui
    fontSize: 0.875rem
    fontWeight: 400
    lineHeight: 1.5
  body-sm:
    fontFamily: system-ui
    fontSize: 0.75rem            # secondary meta lines, e.g. room and teacher
    fontWeight: 400
    lineHeight: 1.45
  label-caps:
    fontFamily: system-ui
    fontSize: 0.6875rem          # eyebrow and stat captions, UPPERCASE, tracked
    fontWeight: 400
    lineHeight: 1.2
    letterSpacing: "0.08em"
  stat:
    fontFamily: system-ui
    fontSize: 1.75rem            # big numbers in the stat strip
    fontWeight: 500
    lineHeight: 1.1

rounded:
  none: 0px                      # stat strip and list rows are square with hairlines
  sm: 8px                        # approx: search field, nav row
  md: 12px                       # approx: grouped setting cards
  lg: 16px                       # approx: modal corner radius measured ~14-16px
  full: 9999px                   # pill buttons and avatars

spacing:
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  xxl: 48px
  container-max: 1000px          # approx: content column width in the dark screenshot
  header-height: 72px            # approx: 84px at screenshot scale

components:
  divider:
    backgroundColor: "{colors.border}"
    height: 1px
  caption:
    backgroundColor: "{colors.background}"
    textColor: "{colors.text-muted}"
    typography: "{typography.label-caps}"
  lead-text:
    backgroundColor: "{colors.background}"
    textColor: "{colors.text-secondary}"
    typography: "{typography.body-lg}"
  modal-scrim:
    backgroundColor: "{colors.scrim}"
  app-header-dark:
    backgroundColor: "{colors.dark-background}"
    textColor: "{colors.dark-text-primary}"
    height: "{spacing.header-height}"
  button-pill-dark:
    backgroundColor: "{colors.dark-surface}"
    textColor: "{colors.dark-text-primary}"
    typography: "{typography.body-md}"
    rounded: "{rounded.full}"
    padding: 8px
    height: 32px
  button-pill-primary-dark:
    backgroundColor: "{colors.dark-primary}"
    textColor: "{colors.dark-on-primary}"
    typography: "{typography.body-md}"
    rounded: "{rounded.full}"
    padding: 8px
    height: 32px
  divider-dark:
    backgroundColor: "{colors.dark-border}"
    height: 1px
  caption-dark:
    backgroundColor: "{colors.dark-background}"
    textColor: "{colors.dark-text-muted}"
    typography: "{typography.label-caps}"
  lead-text-dark:
    backgroundColor: "{colors.dark-background}"
    textColor: "{colors.dark-text-secondary}"
    typography: "{typography.body-lg}"
  nav-row-selected-dark:
    backgroundColor: "{colors.dark-surface-muted}"
    textColor: "{colors.dark-text-primary}"
    typography: "{typography.body-md}"
    rounded: "{rounded.sm}"
    padding: 8px
  app-header:
    backgroundColor: "{colors.background}"
    textColor: "{colors.text-primary}"
    height: "{spacing.header-height}"
  button-pill:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-primary}"
    typography: "{typography.body-md}"
    rounded: "{rounded.full}"
    padding: 8px
    height: 32px
  button-pill-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.body-md}"
    rounded: "{rounded.full}"
    padding: 8px
    height: 32px
  input-search:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-primary}"
    typography: "{typography.body-md}"
    rounded: "{rounded.sm}"
    padding: 8px
    height: 36px
  card-setting-group:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.md}"
    padding: 16px
  stat-strip-cell:
    backgroundColor: "{colors.background}"
    textColor: "{colors.text-primary}"
    typography: "{typography.stat}"
    rounded: "{rounded.none}"
    padding: 16px
  modal:
    backgroundColor: "{colors.background}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.lg}"
    padding: 24px
  nav-row-selected:
    backgroundColor: "{colors.surface-muted}"
    textColor: "{colors.text-primary}"
    typography: "{typography.body-md}"
    rounded: "{rounded.sm}"
    padding: 8px
---

## Overview

UniLink is a quiet, editorial interface for students. The reference shows a
restrained system: warm near-black and warm off-white surfaces, hairline
borders instead of shadows or filled cards, generous whitespace, and a single
type voice. The look comes from hierarchy and typography, not decoration.

The only structural motifs in the reference are:

- An **eyebrow label** (uppercase, tracked, muted) above a large, tight display heading.
- A **stat strip**: one bordered container split by vertical hairlines, each cell a caption, a large number and a muted note. Use it only for real numbers that the backend returns.
- **Ruled lists** (rows separated by 1px lines) for schedules and queues rather than stacked cards.
- **Pill buttons** with an outline and a small trailing arrow glyph for navigation actions.
- A **settings modal** with a left navigation column and a right content pane, content grouped into bordered, rounded groups with label/value rows.

The platform must feel like one product across student and admin surfaces. The
Admin Portal reuses every token here and differs in layout (dense tables, a left
rail, side-by-side review panes), not in colour or type.

## Colors

Two complete themes share identical token names (dark tokens carry a `dark-` prefix in the YAML). Components reference tokens
only (`{colors.surface}`), never hex values. Implementation: CSS custom
properties on `:root` for light and `[data-theme="dark"]` for dark, with the
initial choice taken from `prefers-color-scheme` and persisted per user.

| Token | Light | Dark | Use |
|---|---|---|---|
| background | #fdfdfb | #0e0e0c | page and modal background |
| surface | #ffffff | #171715 | pills, inputs, grouped cards, avatar chip |
| surface-muted | #f2f1ed | #22221e (approx) | selected row, hover |
| border | #e4e2dc | #262622 | every divider and outline, 1px |
| text-primary | #14140f | #f5f4f0 | headings and primary text |
| text-secondary | #5b5951 | #a9a69c | lead text and descriptions |
| text-muted | #8b8b8b | #726f67 | eyebrows, captions, placeholders |
| primary | #14140f | #f5f4f0 | the one filled action colour (ink on paper / paper on ink) |

**Gaps in the reference (do not invent, request or decide with the owner).**
The screenshots contain no accent hue, no error, warning or success colours, no
focus ring, and no modal scrim token. The light-theme scrim sampled as #a8a8a5
is the blurred page behind the modal, not a designed value. Until the owner
supplies these, status meaning (error, pending, verified) must be carried by
**text, icons and shape**, not by new colours. The Verified badge in the
reference is an outlined pill with a check glyph and the word "Verified", which
already works without colour. `REQUIREMENTS_DECISION_LOG.md` item D-040 tracks this.

**Contrast check (executed).** `npx @google/design.md lint DESIGN.md` reports 0 errors and 2 warnings, both real findings from the reference colours themselves: `text-muted` on `background` is 3.35:1 in the light theme (#8b8b8b on #fdfdfb) and 3.85:1 in the dark theme (#726f67 on #0e0e0c), below the 4.5:1 AA minimum for normal text. Rule for implementers: use `text-muted` only for large text or non-essential captions, never for information a user needs to act on (use `text-secondary` there). The owner may supply darker muted values; log the change in `REQUIREMENTS_DECISION_LOG.md` D-040 and re-run the linter. `primary` on `on-primary`, and `text-primary`/`text-secondary` on `background`, pass.

## Typography

One family, differentiated by size, weight and case. The family itself is
**UNCONFIRMED** (see front matter); until confirmed, the token uses `system-ui`
with this fallback stack in CSS:

`system-ui, -apple-system, "Segoe UI", Roboto, "Noto Sans Bengali", "Noto Sans", sans-serif`

Bangla input and display is required by NFR-LOC-01, so a Bengali-capable
fallback is mandatory. Keep body weight light (300 to 400) and reserve 500 for
the display heading, stat numbers and modal title, matching the reference.

Hierarchy: `label-caps` eyebrow above `display`; `body-lg` lead text; `headline`
for section titles; `stat` for numbers; `body-md` default; `body-sm` for meta.

## Layout

- Content sits in one centred column, about 1000px max (`container-max`, approx), with a sticky 72px (approx) header ruled by a 1px `border` line.
- Header: logo tile and wordmark at left, primary navigation centred, search field, theme toggle, notification bell with count and avatar at right.
- Dashboards use two columns of ruled lists under the stat strip. Mobile (360px minimum, NFR-USA-03) collapses to one column and the header nav moves into a menu.
- Spacing scale: 4, 8, 16, 24, 32, 48. Do not introduce other values.
- The Admin Portal uses a left rail plus a content area, and a split review pane for verification (ID image beside entered data and OCR score).

## Elevation & Depth

Depth is created with hairline borders and surface tone, not shadows. The only
elevated element in the reference is the modal, which sits over a dimmed,
blurred page. Do not add drop shadows to cards, lists or buttons. No
glassmorphism on regular surfaces.

## Shapes

Square (`none`) for the stat strip and ruled list rows. `full` for pill buttons
and avatars. `sm` for inputs and nav rows, `md` for grouped setting cards, `lg`
for modals. Radii are approximate measurements from the screenshots.

## Components

- **button-pill**: outlined pill, label plus a small trailing arrow glyph for navigation; `button-pill-primary` for the single main action on a screen.
- **input-search**: icon at left, placeholder text in `text-muted`, optional keyboard hint (`/`) at right.
- **card-setting-group**: bordered, rounded group of label/value rows separated by hairlines. Used for settings and profile facts.
- **stat-strip-cell**: caption (`label-caps`), number (`stat`), muted note (`body-sm`).
- **modal**: left navigation column (search field, icon plus label rows, selected row in `surface-muted`), right content pane with a title, a close button and a footer bar.
- **status text**: states such as In progress, Upcoming, Verified are plain text or an outlined pill with a glyph. No coloured fills.

Every component needs default, hover, focus-visible, disabled, loading and error
states. Focus-visible styles must be visible in both themes (the reference
defines none, see Colors > Gaps).

## Do's and Don'ts

Do
- Use tokens from this file only; a new value requires editing this file first.
- Give every screen an eyebrow, one clear heading and one primary action.
- Show real data or an honest empty state. Show loading and error states.
- Keep admin and student screens in the same visual language.

Don't
- No gradients, glassmorphism, decorative icons, fake charts or placeholder buttons.
- No card grids where a ruled list communicates better.
- No new accent colours until the owner provides them.
- No animation beyond 150ms opacity or transform feedback; respect `prefers-reduced-motion`.
