# kotlinx.serialization : conserver les sérialiseurs générés des messages de signalisation.
-keepclassmembers @kotlinx.serialization.Serializable class fr.penpenic.babyphone.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Supprimer les logs de debug et verbose en release.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
