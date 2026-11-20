package fr.penpenic.babyphone.core.crypto

import android.content.pm.PackageManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.ProviderException
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Clé d'appareil stockée dans l'Android Keystore, non exportable.
 *
 * ECDSA P-256 est retenu car disponible dans le Keystore dès Android 8 (minSdk 26),
 * contrairement à Ed25519 (Android 13+). StrongBox est utilisé quand le téléphone en dispose.
 */
class AndroidDeviceKeyStore(private val context: Context) : DeviceKeyStore {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun publicKey(): ByteArray {
        ensureKeyPair()
        return keyStore.getCertificate(ALIAS).publicKey.encoded
    }

    override fun sign(data: ByteArray): ByteArray {
        ensureKeyPair()
        val privateKey = keyStore.getKey(ALIAS, null) as PrivateKey
        return Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey)
            update(data)
            sign()
        }
    }

    @Synchronized
    private fun ensureKeyPair() {
        if (keyStore.containsAlias(ALIAS)) return
        val wantsStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        if (!wantsStrongBox) {
            generate(strongBox = false)
            return
        }
        // StrongBoxUnavailableException n'existe qu'à partir d'Android 9 : on attrape sa classe
        // parente pour ne pas casser le chargement de la classe sur Android 8.
        try {
            generate(strongBox = true)
        } catch (e: ProviderException) {
            generate(strongBox = false)
        }
    }

    private fun generate(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
            initialize(spec)
            generateKeyPair()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "babyphone_device_key"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}
