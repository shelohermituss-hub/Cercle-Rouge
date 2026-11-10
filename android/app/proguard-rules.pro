# Retire les journaux Android en release : aucune donnée personnelle ne doit finir dans logcat (R-PE-04).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
