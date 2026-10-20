package com.example.util

import android.util.Base64
import com.android.apksig.ApkSigner
import com.example.data.model.SigningConfig
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Professional APK Signing and Alignment Engine powered by Google's official ApkSigner library.
 * Supports simultaneous Scheme v1 (JAR), Scheme v2 (APK Signature Block), Scheme v3 (Key Rotation),
 * and Scheme v4 (.idsig) signing, with automatic 4-byte / 4KB ZipAlign and custom keystore support.
 */
object ApkSignerHelper {

    // Embedded standard AOSP RSA-2048 TestKey PKCS#8 private key (Base64)
    private const val TESTKEY_PRIVATE_KEY_B64 =
        "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCUVUBWgj4tE1gFUTO73EKT" +
        "FHU2NM0FhYO4shUxztM91kks6kaEg3Gu8xpLAOBpGub8a7yfhajt//RzVWEQk7KUvotrmQC5" +
        "3w0VSYoDCuTeZNYH7YWRta1aWznWYHtmxLLf7A6ayCxxb0osjoTgKE0De4sUFiBFV0IBf94O" +
        "Ay5Wi0XOEEfFQtu+MP68sF5zUJ/eNpeABEtzNbGDK149IJUSoHGfACCTZ6KCy7xyXIh3z3xb" +
        "64rrNAoI4lhYVVmvkoAHULDCF9a533PKm7H/KTjVqH1EbfnQjElZFhgic2aG2Emofx87Vauh" +
        "TGWq+HTEmJH4QnqON22tjVJdme/c+aI5AgMBAAECggEABCN2xpMesfpCr+9KjrjAAMOG26rQ" +
        "d5F4kdwiHEAlg6mpY//rVMIoF1YAC0ysdjJCdJN68UZ05qMICnm82g4M4vqIAEoGC6mN/2SM" +
        "XbZdmVjd4d2+JskXW5iE/ha2CrF9PiMjay0flFCjFKgrS+fD8k9tm3NcliKvnEQZiXGMRuzT" +
        "90T4NGfV5Rs+N8PuuyelXA57HDbR1sFKHEakOM9h4fTkm5bPcunknTXYFnKF4MbNCPMaWUNg" +
        "9EX8cit+m47cMkKGHNlECxxryAT7KAnBMrsbpE0b+m47qrUiUQI+xuU4gmskvJBmKieRnVcO" +
        "pSOyRwNNaJBCt/hYWKZtC7VGiwKBgQDAHuM8C67iOqtBC/aY/age+wILXl8EK7liVvsK82+l" +
        "R49vc4PzyBBCznJmcCBlUcoWi01nUnegDZJvR5hQ3VzpPaIV4MRtYksZIIRv+7waQARJlt5X" +
        "B5Pbhzos9qil6bwbhVHih8EjfpsGsGt8tTE3lA7THzUea3oyUyctIBmjxwKBgQDFpzRmU3A7" +
        "LzhFxkP1DJnw3RE+Hv7N9Dv+3szr+HkwAh64Ucm7kYqKveAMrNDq5PanTWnOJFwPTo3DGiCK" +
        "G9tR42mHt58HkMbXWP2up6pEztxcpGzyBcXxgNLiim1UKOfmyP3kv73fEkePJ0IjXUTad5TU" +
        "LySSSdYJ2cIdyuaJ/wKBgQCMFLlo6bfdGhwONJPcX287D5ghA/FR0DO+oV8wT+mC4JeUEu0O" +
        "TjyVCYRebrkgRy+LmoyYnoe8usifvNsxcwYwxpCtVRLEEtxg+NW39RvlI/I8WfXKDStg8MuZ" +
        "R0wWlUNj6aSYAJXxtHmCuhXVeyPku17Hz0Ri5sjVcs3o2DP5twKBgFFc7SMaQd77Jy0B2sOr" +
        "NkuClTB/TeC2I2RdjvmsRSbQGJlDgcmmteM7GlndYfrv2eEw6tci/IqIia7AuJhhszzYYn6t" +
        "xdc1b8UCKxuUhXDhvrCcNwTB/6PUpfr/hNXaKOxF3M4naB/FxJNXU9B++nL8OtXFukZGAsf3" +
        "TgM5nQE9AoGBAKXZNFAw0JCRPWqK57u7Bk8u1Rs7Zht5oAYxkZBgocAXs4tOfnl+nQTPBeGV" +
        "psA98N9zLaPPa4lRTw5vL+OPYAKPhRGAM0DQaVbvYyiW8MeFASW7ErG4xGVpCFDtNylQj6af" +
        "PujFJgZlyp2G1uEzNZUNfz41keP1WglIq1F5bgXf"

    // Embedded standard AOSP RSA-2048 TestKey X.509 certificate in DER format (Base64)
    private const val TESTKEY_CERTIFICATE_B64 =
        "MIIDhjCCAm6gAwIBAgIJAPhDp3ZFEi5QMA0GCSqGSIb3DQEBDAUAMHAxCzAJBgNVBAYTAlVT" +
        "MRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRAwDgYDVQQK" +
        "EwdBbmRyb2lkMRAwDgYDVQQLEwdBbmRyb2lkMRAwDgYDVQQDEwdBbmRyb2lkMCAXDTI2MTAy" +
        "MDIxMTcyN1oYDzIwNTQwMzA3MjExNzI3WjBwMQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2Fs" +
        "aWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEQMA4GA1UEChMHQW5kcm9pZDEQMA4G" +
        "A1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDCCASIwDQYJKoZIhvcNAQEBBQADggEP" +
        "ADCCAQoCggEBAJRVQFaCPi0TWAVRM7vcQpMUdTY0zQWFg7iyFTHO0z3WSSzqRoSDca7zGksA" +
        "4Gka5vxrvJ+FqO3/9HNVYRCTspS+i2uZALnfDRVJigMK5N5k1gfthZG1rVpbOdZge2bEst/s" +
        "DprILHFvSiyOhOAoTQN7ixQWIEVXQgF/3g4DLlaLRc4QR8VC274w/rywXnNQn942l4AES3M1" +
        "sYMrXj0glRKgcZ8AIJNnooLLvHJciHfPfFvrius0CgjiWFhVWa+SgAdQsMIX1rnfc8qbsf8p" +
        "ONWofURt+dCMSVkWGCJzZobYSah/HztVq6FMZar4dMSYkfhCeo43ba2NUl2Z79z5ojkCAwEA" +
        "AaMhMB8wHQYDVR0OBBYEFNQoQ1juVkHHLH/yM8PYQWobLO/mMA0GCSqGSIb3DQEBDAUAA4IB" +
        "AQAYVhsjpgk3Qo4LQKM19wsj8aLib0LExtdMDPsVlOvAb0Hfv1bkHipzySF++oyvGkkRnfA7" +
        "1/RnJggRPM9+kpFoVwQjgWLVNxrNyriB9b9p8vpalAgW0IUIlLIB86CRNqFVLA10gKFcAQO8" +
        "LaijrE6NAlQ8QF7J8MP6iOzXkUNSyETGw7Z0f2R596xY9PnrgBHOQRXWRUKl1CNgowqikO5L" +
        "dx4Qz2Lo7cq9EKTnY//auciEmgYd7k5RygK4HfHj6GBQEIZSGbtOlyeeHHhg+Cm5101hmeSL" +
        "QETAFNlzYo9kfO0pGloAm8L7NfG2wUGCIksWyCgvExh/t44rKq+fYF+j"

    /**
     * Executes the complete ZipAlign and Signing pipeline on [apkFile].
     *
     * @param apkFile The target APK file to align and sign.
     * @param config The signing configuration options (schemes, custom keystore, etc.).
     * @return The final aligned and signed APK file.
     */
    fun signAndAlignApk(
        apkFile: File,
        config: SigningConfig = SigningConfig()
    ): File {
        if (!apkFile.exists() || apkFile.length() == 0L) return apkFile

        val alignedFile = File(apkFile.parentFile, "${apkFile.nameWithoutExtension}_temp_aligned.apk")
        val signedFile = File(apkFile.parentFile, "${apkFile.nameWithoutExtension}_temp_signed.apk")

        try {
            // 1. Step 1: 4-byte and 4096-byte ZipAlign
            val alignSuccess = ZipAligner.align(apkFile, alignedFile)
            val fileToSign = if (alignSuccess && alignedFile.exists() && alignedFile.length() > 0L) {
                alignedFile
            } else {
                apkFile
            }

            // 2. Step 2: Load signing credentials (Custom Keystore or standard embedded TestKey)
            val credentials = loadCredentials(config)
            val signerConfig = ApkSigner.SignerConfig.Builder(
                credentials.alias,
                credentials.privateKey,
                listOf(credentials.certificate)
            ).build()

            // 3. Step 3: Configure ApkSigner schemes
            val isAuto = config.isAutoSigning
            val v1 = if (isAuto) true else config.v1Enabled
            val v2 = if (isAuto) true else config.v2Enabled
            val v3 = if (isAuto) true else config.v3Enabled
            val v4 = if (isAuto) false else config.v4Enabled

            val signerBuilder = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(fileToSign)
                .setOutputApk(signedFile)
                .setV1SigningEnabled(v1)
                .setV2SigningEnabled(v2)
                .setV3SigningEnabled(v3)
                .setV4SigningEnabled(v4)
                .setAlignmentPreserved(true)
                .setCreatedBy("OmniSplitX")

            if (v4) {
                val idsigFile = File(apkFile.parentFile, "${apkFile.name}.idsig")
                signerBuilder.setV4SignatureOutputFile(idsigFile)
            }

            // 4. Step 4: Execute signature scheme generation
            val signer = signerBuilder.build()
            signer.sign()

            // 5. Step 5: Replace original APK with finalized signed & aligned artifact
            if (signedFile.exists() && signedFile.length() > 0L) {
                val originalPath = apkFile.absolutePath
                apkFile.delete()
                val finalFile = File(originalPath)
                signedFile.renameTo(finalFile)
                return finalFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { alignedFile.delete() } catch (_: Exception) {}
            try { signedFile.delete() } catch (_: Exception) {}
        }

        return apkFile
    }

    private data class SigningCredentials(
        val alias: String,
        val privateKey: PrivateKey,
        val certificate: X509Certificate
    )

    private fun loadCredentials(config: SigningConfig): SigningCredentials {
        if (config.useCustomKeystore && !config.customKeystorePath.isNullOrBlank()) {
            val ksFile = File(config.customKeystorePath)
            if (ksFile.exists()) {
                try {
                    val ksType = if (ksFile.name.endsWith(".p12", ignoreCase = true) ||
                        ksFile.name.endsWith(".pfx", ignoreCase = true)) {
                        "PKCS12"
                    } else if (ksFile.name.endsWith(".bks", ignoreCase = true)) {
                        "BKS"
                    } else {
                        "JKS"
                    }
                    val ks = KeyStore.getInstance(ksType)
                    val pw = config.keystorePassword.toCharArray()
                    FileInputStream(ksFile).use { ks.load(it, pw) }

                    val alias = if (config.keyAlias.isNotBlank() && ks.containsAlias(config.keyAlias)) {
                        config.keyAlias
                    } else {
                        ks.aliases().nextElement()
                    }

                    val keyPw = if (config.keyPassword.isNotBlank()) {
                        config.keyPassword.toCharArray()
                    } else {
                        pw
                    }

                    val entry = ks.getEntry(alias, KeyStore.PasswordProtection(keyPw)) as? KeyStore.PrivateKeyEntry
                    if (entry != null) {
                        val cert = entry.certificate as? X509Certificate
                        if (cert != null) {
                            return SigningCredentials(alias, entry.privateKey, cert)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Fallback: Embedded standard AOSP TestKey credentials
        return loadEmbeddedTestKey()
    }

    private fun loadEmbeddedTestKey(): SigningCredentials {
        val keyBytes = Base64.decode(TESTKEY_PRIVATE_KEY_B64, Base64.DEFAULT)
        val privKeySpec = PKCS8EncodedKeySpec(keyBytes)
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(privKeySpec)

        val certBytes = Base64.decode(TESTKEY_CERTIFICATE_B64, Base64.DEFAULT)
        val certFactory = CertificateFactory.getInstance("X.509")
        val certificate = certFactory.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate

        return SigningCredentials("CERT", privateKey, certificate)
    }
}
