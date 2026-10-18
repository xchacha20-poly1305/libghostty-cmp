//! One terminal as the Kotlin GhosttyTerminalSession sees it: the VT
//! stream, its render state, and the effects the stream produced since
//! Kotlin last drained them.
const Session = @This();

const std = @import("std");
const builtin = @import("builtin");
const vt = @import("ghostty-vt");
const jni = @import("jni.zig");

const Handler = vt.TerminalStream.Handler;
const Effects = Handler.Effects;

/// Bits of `pending_events`; mirrored by `GhosttyVt.EVENT_*`.
pub const Event = struct {
    pub const bell: i32 = 1;
    pub const title: i32 = 1 << 1;
    pub const clipboard: i32 = 1 << 2;
    pub const pwd: i32 = 1 << 3;
    pub const notification: i32 = 1 << 4;
    pub const progress: i32 = 1 << 5;
    pub const colors: i32 = 1 << 6;
};

const max_pending_notifications = 16;
const kitty_image_storage_limit = 128 * 1024 * 1024;

pub const Notification = struct {
    title: []u8,
    body: []u8,
};

/// The I/O the terminal needs for Kitty graphics file transmission.
/// TinyIo has no NT implementation, so Windows uses the threaded Io.
const Io = if (builtin.os.tag == .windows) struct {
    threaded: std.Io.Threaded = .init_single_threaded,

    fn io(self: *@This()) std.Io {
        return self.threaded.io();
    }

    fn deinit(self: *@This()) void {
        self.threaded.deinit();
    }
} else struct {
    tiny: vt.TinyIo = .init,

    fn io(self: *@This()) std.Io {
        return self.tiny.io();
    }

    fn deinit(_: *@This()) void {}
};

alloc: std.mem.Allocator,
io: Io = .{},
terminal: vt.Terminal,
stream: vt.TerminalStream,
render: vt.RenderState = .empty,

cell_width_px: u32 = 0,
cell_height_px: u32 = 0,
mouse_button_down: bool = false,

/// Bytes the terminal wrote back to the pty during the current call.
pty_out: std.ArrayList(u8) = .empty,
pending_events: i32 = 0,
clipboard_text: ?[]u8 = null,
xtversion: []u8,
notifications: std.ArrayList(Notification) = .empty,
progress_state: i32 = 0,
progress_percent: i32 = -1,
color_scheme_dark: bool = true,
background: i32 = 0,

/// The GhosttyTerminalSession that owns this terminal, for clipboard reads
/// the program requests. `env` is only set while Kotlin is inside a call
/// that can run the stream.
owner: jni.Object,
env: ?*jni.Env = null,

pub const Options = struct {
    cols: u16,
    rows: u16,
    max_scrollback_lines: usize,
    xtversion: []const u8,
    terminfo_name: ?[]const u8,
    cursor_style: vt.CursorStyle,
    cursor_blink: bool,
};

pub fn create(alloc: std.mem.Allocator, owner: jni.Object, opts: Options) !*Session {
    if (opts.cols == 0 or opts.rows == 0) return error.InvalidValue;

    const self = try alloc.create(Session);
    errdefer alloc.destroy(self);

    const xtversion = try alloc.dupe(u8, opts.xtversion);
    errdefer alloc.free(xtversion);

    self.* = .{
        .alloc = alloc,
        .terminal = undefined,
        .stream = undefined,
        .xtversion = xtversion,
        .owner = owner,
    };
    errdefer self.io.deinit();

    self.terminal = try .init(self.io.io(), alloc, .{
        .cols = opts.cols,
        .rows = opts.rows,
        .max_scrollback_lines = opts.max_scrollback_lines,
        .kitty_image_storage_limit = kitty_image_storage_limit,
        .colors = .{
            .background = .init(.{ .r = 0, .g = 0, .b = 0 }),
            .foreground = .init(.{ .r = 0xFF, .g = 0xFF, .b = 0xFF }),
            .cursor = .unset,
            .palette = .default,
        },
        .default_cursor_style = opts.cursor_style,
        .default_cursor_blink = opts.cursor_blink,
    });
    errdefer self.terminal.deinit(alloc);

    // The host decides whether its shell redraws prompts on resize; without
    // shell integration Ghostty must not assume it does (OSC 133;A;redraw=1
    // still opts in).
    self.terminal.flags.shell_redraws_prompt = .false;

    var handler = self.terminal.vtHandler();
    handler.effects = .{
        .write_pty = &writePty,
        .bell = &bell,
        .desktop_notification = &desktopNotification,
        .drag_and_drop = null,
        .color_scheme = &colorScheme,
        .device_attributes = &deviceAttributes,
        .enquiry = null,
        .size = &size,
        .title_changed = &titleChanged,
        .pwd_changed = &pwdChanged,
        .progress_report = &progressReport,
        .clipboard_write = &clipboardWrite,
        .clipboard_read = &clipboardRead,
        .xtversion = &xtversionReport,
    };
    if (opts.terminfo_name) |name| {
        if (name.len > 0 and name.len <= Handler.max_terminfo_name_bytes) {
            handler.terminfo_name = try alloc.dupe(u8, name);
        }
    }
    self.stream = .init(.{ .allocator = alloc, .handler = handler });

    self.background = self.effectiveBackground();
    return self;
}

pub fn destroy(self: *Session) void {
    const alloc = self.alloc;
    if (self.stream.handler.terminfo_name) |name| alloc.free(name);
    self.stream.deinit();
    self.render.deinit(alloc);
    self.terminal.deinit(alloc);
    self.io.deinit();
    self.pty_out.deinit(alloc);
    if (self.clipboard_text) |text| alloc.free(text);
    alloc.free(self.xtversion);
    for (self.notifications.items) |n| freeNotification(alloc, n);
    self.notifications.deinit(alloc);
    alloc.destroy(self);
}

pub fn freeNotification(alloc: std.mem.Allocator, n: Notification) void {
    alloc.free(n.title);
    alloc.free(n.body);
}

/// Feeds program output to the terminal. The pty response is left in
/// `pty_out`.
pub fn write(self: *Session, env: *jni.Env, data: []const u8) void {
    self.pty_out.clearRetainingCapacity();
    self.env = env;
    defer self.env = null;
    self.stream.nextSlice(data);

    // OSC 11 changes the background without any effect callback; diff the
    // effective color around each write to surface it as an event.
    const background = self.effectiveBackground();
    if (background != self.background) {
        self.background = background;
        self.pending_events |= Event.colors;
    }
}

pub fn resize(self: *Session, cols: u16, rows: u16, cell_width_px: u32, cell_height_px: u32) !void {
    self.cell_width_px = cell_width_px;
    self.cell_height_px = cell_height_px;
    try self.stream.handler.resize(.{
        .cols = cols,
        .rows = rows,
        .cell_size_px = .{ .width = cell_width_px, .height = cell_height_px },
    });
    self.markDirty();
}

/// ghostty's Screen.select does not mark the selected rows dirty, so
/// selection and theme changes force a full redraw.
pub fn markDirty(self: *Session) void {
    self.render.dirty = .full;
}

pub fn effectiveBackground(self: *Session) i32 {
    const rgb = self.terminal.colors.background.get() orelse return 0;
    return argb(rgb);
}

pub fn argb(rgb: vt.color.RGB) i32 {
    return @bitCast(@as(u32, 0xFF000000) |
        (@as(u32, rgb.r) << 16) |
        (@as(u32, rgb.g) << 8) |
        rgb.b);
}

fn fromHandler(handler: *Handler) *Session {
    const stream: *vt.TerminalStream = @fieldParentPtr("handler", handler);
    return @fieldParentPtr("stream", stream);
}

fn EffectResult(comptime name: []const u8) type {
    const Fn = @typeInfo(@typeInfo(@FieldType(Effects, name)).optional.child).pointer.child;
    return @typeInfo(Fn).@"fn".return_type.?;
}

fn writePty(handler: *Handler, data: []const u8) void {
    const self = fromHandler(handler);
    self.pty_out.appendSlice(self.alloc, data) catch
        std.log.warn("dropping {d} bytes of pty response: out of memory", .{data.len});
}

fn bell(handler: *Handler) void {
    fromHandler(handler).pending_events |= Event.bell;
}

fn titleChanged(handler: *Handler) void {
    fromHandler(handler).pending_events |= Event.title;
}

fn pwdChanged(handler: *Handler) void {
    fromHandler(handler).pending_events |= Event.pwd;
}

fn desktopNotification(handler: *Handler, notification: vt.StreamAction.ShowDesktopNotification) void {
    const self = fromHandler(handler);
    if (self.notifications.items.len >= max_pending_notifications) return;
    const title = self.alloc.dupe(u8, notification.title) catch return;
    const body = self.alloc.dupe(u8, notification.body) catch {
        self.alloc.free(title);
        return;
    };
    self.notifications.append(self.alloc, .{ .title = title, .body = body }) catch {
        freeNotification(self.alloc, .{ .title = title, .body = body });
        return;
    };
    self.pending_events |= Event.notification;
}

fn progressReport(handler: *Handler, report: vt.osc.Command.ProgressReport) void {
    const self = fromHandler(handler);
    self.progress_state = @intFromEnum(report.state);
    self.progress_percent = if (report.progress) |p| p else -1;
    self.pending_events |= Event.progress;
}

fn colorScheme(handler: *Handler) EffectResult("color_scheme") {
    return if (fromHandler(handler).color_scheme_dark) .dark else .light;
}

// The attributes ghostty itself reports (termio/stream_handler.zig): DA1
// "CSI ? 62 ; 22 c", DA2 "CSI > 1 ; 10 ; 0 c".
fn deviceAttributes(_: *Handler) EffectResult("device_attributes") {
    return .{ .secondary = .{ .firmware_version = 10 } };
}

fn size(handler: *Handler) EffectResult("size") {
    const self = fromHandler(handler);
    if (self.cell_width_px == 0 or self.cell_height_px == 0) return null;
    return .{
        .rows = self.terminal.rows,
        .columns = self.terminal.cols,
        .cell_width = self.cell_width_px,
        .cell_height = self.cell_height_px,
    };
}

fn xtversionReport(handler: *Handler) []const u8 {
    return fromHandler(handler).xtversion;
}

fn clipboardWrite(handler: *Handler, write_: vt.clipboard.Write) void {
    const self = fromHandler(handler);
    if (write_.location != .standard) return write_.reply(.unsupported);

    const text: []const u8 = if (write_.contents.len == 0) "" else for (write_.contents) |content| {
        if (vt.clipboard.isTextMime(content.mime)) break content.data;
    } else return write_.reply(.unsupported);

    const copy = self.alloc.dupe(u8, text) catch return write_.reply(.io_error);
    if (self.clipboard_text) |old| self.alloc.free(old);
    self.clipboard_text = copy;
    self.pending_events |= Event.clipboard;
    write_.reply(.{ .success = .{} });
}

/// Reads go up to Kotlin, which owns the system clipboard and asks the user
/// before a program may read it.
fn clipboardRead(handler: *Handler, read: vt.clipboard.Read) void {
    const self = fromHandler(handler);
    const env = self.env orelse return read.reply(.denied);
    const methods = owner_methods orelse return read.reply(.denied);

    const text_mime: ?[]const u8 = for (read.mimes) |mime| {
        if (vt.clipboard.isTextMime(mime)) break mime;
    } else null;

    const available: []const []const u8 = available: {
        if (!read.list) break :available &.{};
        const has_text = env.call("CallBooleanMethodA", .{ self.owner, methods.clipboard_has_text, null });
        if (env.clearException() or has_text == 0) break :available &.{};
        break :available &.{"text/plain"};
    };

    const mime = text_mime orelse return read.reply(.{ .success = .{ .available = available } });

    const args = [_]jni.Value{.{ .z = @intFromBool(read.granted) }};
    const data: jni.ByteArray = @ptrCast(env.call("CallObjectMethodA", .{ self.owner, methods.read_clipboard_for_program, &args }));
    if (env.clearException() or data == null) return read.reply(.denied);
    defer env.call("DeleteLocalRef", .{data});

    const text = env.byteArrayAlloc(self.alloc, data) catch return read.reply(.io_error);
    defer self.alloc.free(text);
    const contents = [_]vt.clipboard.Content{.{ .mime = mime, .data = text }};
    read.reply(.{ .success = .{
        .contents = if (text.len > 0) &contents else &.{},
        .available = available,
        .remember = true,
    } });
}

/// Methods on GhosttyTerminalSession the clipboard read effect calls,
/// resolved once in JNI_OnLoad.
pub const OwnerMethods = struct {
    clipboard_has_text: jni.MethodId,
    read_clipboard_for_program: jni.MethodId,
};

pub var owner_methods: ?OwnerMethods = null;
