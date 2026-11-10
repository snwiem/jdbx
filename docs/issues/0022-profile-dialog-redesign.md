# 0022: Profile dialog redesign

**What to build:** The Edit Connection Profile dialog (and the two small modals for the masked password and the removal confirmation) get a clear form layout: a framed panel with a title bar, a two-column grid with aligned labels and visible input fields, a visible cursor, buttons, and errors shown inside the dialog. The Profile store and the dialog model stay as they are. The manual test of issue 0013 showed that the current plain list of `Label: value` rows blends into the Transcript and is confusing (see `docs/assets/profile_dialog.png`).

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] The dialog is a framed panel with a border and a title bar. The title says what the user does: "Edit Connection Profile", "New Connection Profile" or "Connect". The panel is opaque: no Transcript text shows through it
- [ ] The panel is centered and 4 columns narrower on each side than the screen (width = screen width minus 8). It is not designed small: it uses the available height for the form, with at least one blank row above and below it where the screen allows. On small screens it shrinks gracefully and stays usable down to 60 columns by 20 rows. Below that it shows a short "terminal too small" note and keeps Esc working
- [ ] The form is a two-column grid. Labels sit in one aligned column, and each value sits in a visible input field of the same left edge and the full remaining width. Related fields are grouped under short group headings: Profile (Name), Connection (JDBC URL, or Host, Port and Database), Credentials (User, Password), Options (Read-only, Schema, Driver properties)
- [ ] The focused field is clearly marked (reverse or accent color) and shows a visible text cursor. Typed text scrolls horizontally inside a field that is too narrow
- [ ] "JDBC URL" and "Host, Port, Database" are shown as alternatives with a one-line hint: "Either a JDBC URL, or host, port and database". The unused alternative is dimmed while the other one has text (the model still validates)
- [ ] Read-only is a choice, not free text: default, on or off. `Space`, `Left` and `Right` change it. The label says what "default" means (the global setting)
- [ ] Driver properties keep the `key=value;key=value` syntax but have a hint line under the field
- [ ] A button row has `[ Save ]`, `[ Connect ]` (only when connecting is possible) and `[ Cancel ]`. `Tab` and `Shift-Tab` move through the fields and then the buttons. `Enter` activates the focused button and moves to the next field on a field. The existing keys keep working: `Ctrl-S` saves, `Ctrl-G` connects without saving, `Esc` cancels
- [ ] Validation errors (from the dialog model) appear inside the dialog, above the buttons, in the error style. The dialog stays open
- [ ] A one-line key hint sits at the bottom inside the frame
- [ ] The masked password prompt and the removal confirmation use the same frame and title style, in a smaller panel (same 4-column rule on narrow screens, centered, and a sensible maximum width)
- [ ] The password is shown as a fixed number of mask characters that does not reveal its length. The password never appears in rendered cells, terminal output or logs
- [ ] With `NO_COLOR` the layout still works: the focus, the group headings and the errors differ by style or text, and the border uses plain characters
- [ ] Screen tests (`Frame.forTesting`) check: width margin of 4 on each side, border and title, label alignment, focus marker and cursor, long values, error inside the dialog, a small screen (60x20), a too-small screen, `NO_COLOR`, wide characters, and the password mask
- [ ] The existing dialog logic tests (`ProfileDialogModelTest`, `ProfileCommandsIT` and others) stay green. Logic stays in plain classes and is tested without a terminal
