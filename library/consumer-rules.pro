# The JNI library registers GhosttyVt's natives by name in JNI_OnLoad, with
# signatures that name GhosttyTerminalSession, and calls back into the session.
-keepclasseswithmembernames class io.github.xchacha20_poly1305.libghostty.GhosttyVt {
    native <methods>;
}
# These callbacks have no caller on the Java side, so keeping only their names
# lets the shrinker delete them; JNI_OnLoad then fails to find them and the
# library never loads.
-keep class io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession {
    private boolean clipboardHasText();
    private byte[] readClipboardForProgram(boolean);
}
