package fr.penpenic.babyphone.role

/** Rôle de ce téléphone, choisi au premier lancement. */
enum class Role {
    BABY,
    PARENT;

    companion object {
        fun fromStored(value: String?): Role? = entries.firstOrNull { it.name == value }
    }
}
