//! Freestanding browser ABI. Input buffers belong to the caller; result bytes remain valid until
//! the next call. The Kotlin adapter copies results immediately and recreates views after memory growth.
const std = @import("std");
const vt = @import("ghostty-vt");
const Session = @import("Session.zig");
const ops = @import("Operations.zig");
const snapshot = @import("snapshot.zig");
const alloc = std.heap.wasm_allocator;
var result: std.ArrayList(u8) = .empty;

pub const std_options: std.Options = .{ .logFn = discardLog };
fn discardLog(comptime _: std.log.Level, comptime _: @EnumLiteral(), comptime _: []const u8, _: anytype) void {}

export fn ghostty_abi_version() u32 {
    return 2;
}
export fn ghostty_alloc(len: usize) usize {
    return @intFromPtr((alloc.alloc(u8, len) catch return 0).ptr);
}
export fn ghostty_free(ptr: usize, len: usize) void {
    alloc.free(@as([*]u8, @ptrFromInt(ptr))[0..len]);
}
export fn ghostty_result_ptr() usize {
    return @intFromPtr(result.items.ptr);
}
export fn ghostty_result_len() usize {
    return result.items.len;
}

fn bytes(data: []const u8) i32 {
    result.appendSlice(alloc, data) catch return -1;
    return 0;
}
fn encoded(encode: anytype, args: anytype) i32 {
    var out: std.Io.Writer.Allocating = .init(alloc);
    defer out.deinit();
    @call(.auto, encode, .{&out.writer} ++ args) catch return -1;
    return bytes(out.written());
}
fn formatText(s: *Session, selection: ?vt.Selection) i32 {
    var formatter: vt.formatter.TerminalFormatter = .init(&s.terminal, .{ .emit = .plain, .unwrap = true, .trim = true });
    formatter.extra = .none;
    if (selection) |sel| formatter.content = .{ .selection = sel };
    var out: std.Io.Writer.Allocating = .init(alloc);
    defer out.deinit();
    formatter.format(&out.writer) catch return -1;
    return bytes(out.written());
}

// Mirrored by GhosttyWebAssembly.kt. Numeric values are private to the Wasm ABI.
const Operation = enum(i32) {
    create = 0,
    free = 1,
    write = 2,
    resize = 3,
    take_events = 4,
    title = 5,
    take_clipboard = 6,
    pwd = 7,
    take_notification = 8,
    progress = 9,
    color_scheme = 10,
    focus = 11,
    hyperlink = 12,
    theme = 13,
    parse_color = 15,
    scroll = 16,
    scroll_bottom = 17,
    scrollbar = 18,
    viewport_state = 19,
    snapshot = 20,
    kitty_placements = 21,
    kitty_image = 22,
    encode_key = 23,
    encode_mouse = 24,
    select_word = 25,
    select_all = 26,
    set_selection = 27,
    clear_selection = 28,
    selection_text = 29,
    viewport_text = 30,
    is_paste_safe = 31,
    encode_paste = 32,
    transcript = 33,
    background = 34,
    is_paste_safe_with_mode = 35,
};

/// Operation numbers are mirrored in GhosttyWasmBackend. Scalar returns use i32; arrays use result bytes.
export fn ghostty_call(op: i32, handle: usize, a: i32, b: i32, c: i32, d: i32, e: i32, f: i32, ptr: usize, len: usize) i32 {
    result.clearRetainingCapacity();
    const operation = std.enums.fromInt(Operation, op) orelse return -1;
    const data: []const u8 = if (len == 0) &.{} else @as([*]const u8, @ptrFromInt(ptr))[0..len];
    if (operation == .create) {
        const split = std.mem.indexOfScalar(u8, data, 0) orelse data.len;
        const s = Session.create(alloc, .{}, .{
            .cols = std.math.cast(u16, a) orelse return 0,
            .rows = std.math.cast(u16, b) orelse return 0,
            .max_scrollback_lines = @intCast(@max(c, 0)),
            .image_storage_limit = 0,
            .xtversion = data[0..split],
            .terminfo_name = if (split < data.len) data[split + 1 ..] else null,
            .cursor_style = std.enums.fromInt(vt.CursorStyle, d) orelse .block,
            .cursor_blink = e != 0,
            .grapheme_cluster = f != 0,
        }) catch return 0;
        return @bitCast(@as(u32, @intCast(@intFromPtr(s))));
    }
    if (operation == .parse_color) return Session.argb(vt.color.RGB.parse(data) catch return 0);
    if (operation == .is_paste_safe) return @intFromBool(vt.input.isSafePaste(data));
    const s: *Session = @ptrFromInt(handle);
    const t = &s.terminal;
    switch (operation) {
        .free => s.destroy(),
        .write => {
            s.write(data);
            return bytes(s.pty_out.items);
        },
        .resize => s.resize(std.math.cast(u16, a) orelse return -1, std.math.cast(u16, b) orelse return -1, std.math.cast(u32, c) orelse return -1, std.math.cast(u32, d) orelse return -1) catch return -1,
        .take_events => {
            const flags = s.pending_events;
            s.pending_events = 0;
            return flags;
        },
        .title => return bytes(t.getTitle() orelse &.{}),
        .take_clipboard => {
            const value = s.clipboard_text orelse return 0;
            defer alloc.free(value);
            s.clipboard_text = null;
            return bytes(value);
        },
        .pwd => return bytes(t.getPwd() orelse &.{}),
        .take_notification => return 0, // Browser hosts do not surface system notifications.
        .progress => {
            const value = [_]i32{ s.progress_state, s.progress_percent };
            return bytes(std.mem.sliceAsBytes(&value));
        },
        .color_scheme => {
            const dark = a != 0;
            const changed = s.color_scheme_dark != dark;
            s.color_scheme_dark = dark;
            if (changed and t.modes.get(.report_color_scheme)) {
                const scheme: vt.device_status.ColorScheme = if (dark) .dark else .light;
                return encoded(vt.device_status.encodeColorSchemeReport, .{scheme});
            }
        },
        .focus => {
            if (t.modes.get(.focus_event)) return encoded(vt.input.encodeFocus, .{@as(vt.input.FocusEvent, if (a != 0) .gained else .lost)});
        },
        .hyperlink => {
            const pin = ops.viewportPin(t, a, b) orelse return 0;
            const page = pin.node.page();
            const cell = pin.rowAndCell().cell;
            if (!cell.hyperlink) return 0;
            const id = page.lookupHyperlink(cell) orelse return 0;
            return bytes(page.hyperlink_set.get(page.memory, id).uri.slice(page.memory));
        },
        .theme => {
            // Theme values are little-endian i64: foreground, background, cursor, palette.
            if (data.len < 24 or data.len % 8 != 0) return -1;
            const colors = &t.colors;
            colors.foreground.default = ops.rgbFromArgb(std.mem.readInt(i64, data[0..8], .little));
            colors.background.default = ops.rgbFromArgb(std.mem.readInt(i64, data[8..16], .little));
            colors.cursor.default = ops.rgbFromArgb(std.mem.readInt(i64, data[16..24], .little));
            var palette = vt.color.default;
            for (0..@min(256, (data.len - 24) / 8)) |i| {
                if (ops.rgbFromArgb(std.mem.readInt(i64, data[24 + i * 8 ..][0..8], .little))) |rgb| palette[i] = rgb;
            }
            colors.palette.changeDefault(alloc, palette) catch return -1;
            t.flags.dirty.palette = true;
            s.background = s.effectiveBackground();
            s.markDirty();
        },
        .scroll => t.scrollViewport(.{ .delta = a }),
        .scroll_bottom => t.scrollViewport(.bottom),
        .scrollbar => {
            const bar = t.screens.active.pages.scrollbar();
            const values = [_]i64{ @intCast(bar.total), @intCast(bar.offset), @intCast(bar.len) };
            return bytes(std.mem.sliceAsBytes(&values));
        },
        .viewport_state => {
            var flags: i32 = 0;
            if (t.screens.active_key == .alternate) flags |= 1;
            if (t.modes.get(.mouse_event_x10) or t.modes.get(.mouse_event_normal) or t.modes.get(.mouse_event_button) or t.modes.get(.mouse_event_any)) flags |= 2;
            if (t.modes.get(.synchronized_output)) flags |= 4;
            return flags;
        },
        .snapshot => {
            result.resize(alloc, @intCast(@max(a, 0))) catch return -1;
            if (b != 0) s.markDirty();
            return snapshot.write(s, result.items);
        },
        .kitty_placements, .kitty_image => return 0, // Kitty images are outside the browser feature set.
        .encode_key => return encoded(vt.input.encodeKey, .{ vt.input.KeyEvent{
            .key = std.enums.fromInt(vt.input.Key, a) orelse .unidentified,
            .action = std.enums.fromInt(vt.input.KeyAction, b) orelse .press,
            .mods = ops.mods(c),
            .unshifted_codepoint = std.math.cast(u21, d) orelse 0,
            .composing = e != 0,
            .utf8 = data,
        }, vt.input.KeyEncodeOptions.fromTerminal(t) }),
        .encode_mouse => {
            const action = std.enums.fromInt(vt.input.MouseAction, a) orelse return 0;
            const button: ?vt.input.MouseButton = switch (std.enums.fromInt(vt.input.MouseButton, b) orelse .unknown) {
                .unknown => null,
                else => |value| value,
            };
            if (button) |value| switch (value) {
                .left, .right, .middle => switch (action) {
                    .press => s.mouse_button_down = true,
                    .release => s.mouse_button_down = false,
                    .motion => {},
                },
                else => {},
            };
            var opts: vt.input.MouseEncodeOptions = .fromTerminal(t, .{ .screen = .{ .width = t.cols, .height = t.rows }, .cell = .{ .width = 1, .height = 1 }, .padding = .{} });
            opts.any_button_pressed = s.mouse_button_down;
            return encoded(vt.input.encodeMouse, .{ vt.input.MouseEncodeEvent{ .action = action, .button = button, .mods = ops.mods(e), .pos = .{ .x = @floatFromInt(c), .y = @floatFromInt(d) } }, opts });
        },
        .select_word => {
            const pin = ops.viewportPin(t, a, b) orelse return 0;
            return @intFromBool(ops.installSelection(s, t.screens.active.selectWord(pin, &vt.selection_codepoints.default_word_boundaries)));
        },
        .select_all => return @intFromBool(ops.installSelection(s, t.screens.active.selectAll())),
        .set_selection => {
            const start = ops.viewportPin(t, a, b) orelse return 0;
            const end = ops.viewportPin(t, c, d) orelse return 0;
            return @intFromBool(ops.installSelection(s, .init(start, end, false)));
        },
        .clear_selection => {
            t.screens.active.clearSelection();
            s.markDirty();
        },
        .selection_text => return formatText(s, t.screens.active.selection orelse return 0),
        .viewport_text => {
            const start = ops.viewportPin(t, 0, 0) orelse return 0;
            const end = ops.viewportPin(t, t.cols - 1, t.rows - 1) orelse return 0;
            return formatText(s, .init(start, end, false));
        },
        .encode_paste => {
            s.pty_out.clearRetainingCapacity();
            _ = s.stream.handler.paste(.{ .contents = .{ .memory = &.{.{ .mime = "text/plain", .data = data }} }, .allow_unsafe = true }) catch return -1;
            return bytes(s.pty_out.items);
        },
        .transcript => return formatText(s, null),
        .background => return s.effectiveBackground(),
        .is_paste_safe_with_mode => return @intFromBool(vt.input.isSafePasteWith(data, .fromTerminal(t))),
        else => return -1,
    }
    return 0;
}
