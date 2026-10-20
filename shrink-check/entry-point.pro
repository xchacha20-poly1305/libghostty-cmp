# Only the entry point. Everything the library needs at run time has to come
# from the rules its jar ships under META-INF/proguard/.
-keep class io.github.xchacha20_poly1305.libghostty.shrinkcheck.MainKt {
    public static void main(java.lang.String[]);
}
