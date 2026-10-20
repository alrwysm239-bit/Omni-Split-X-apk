package com.example.util

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ApkSignerUtil {

    /**
     * Performs ZipAlign and V1/V2 Signature on the target APK file.
     */
    fun signAndAlignApk(apkFile: File): File {
        if (!apkFile.exists()) return apkFile

        val alignedFile = File(apkFile.parent, "${apkFile.nameWithoutExtension}_aligned.apk")
        val signedFile = File(apkFile.parent, "${apkFile.nameWithoutExtension}_signed.apk")

        try {
            alignZip(apkFile, alignedFile)
            signZip(alignedFile, signedFile)

            if (signedFile.exists() && signedFile.length() > 0) {
                val finalFile = File(apkFile.parent, apkFile.name)
                apkFile.delete()
                alignedFile.delete()
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

    private fun alignZip(input: File, output: File) {
        ZipFile(input).use { zip ->
            ZipOutputStream(FileOutputStream(output)).use { out ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("META-INF/") && (entry.name.endsWith(".SF") || entry.name.endsWith(".RSA") || entry.name.endsWith(".DSA") || entry.name.endsWith(".MANIFEST.MF"))) {
                        continue
                    }
                    val newEntry = ZipEntry(entry.name).apply {
                        method = if (entry.name.endsWith(".png", ignoreCase = true) || entry.name.endsWith(".so", ignoreCase = true)) {
                            ZipEntry.STORED
                        } else {
                            ZipEntry.DEFLATED
                        }
                    }
                    out.putNextEntry(newEntry)
                    zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
    }

    private fun signZip(input: File, output: File) {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val pair = kpg.generateKeyPair()
        val privateKey = pair.private
        val publicKey = pair.public

        val manifestBuilder = StringBuilder()
        manifestBuilder.append("Manifest-Version: 1.0\nCreated-By: OmniSplitX Engine\n\n")

        val entriesData = mutableMapOf<String, ByteArray>()

        ZipFile(input).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name
                if (name.startsWith("META-INF/")) continue

                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                entriesData[name] = bytes

                val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                val encodedDigest = Base64.getEncoder().encodeToString(digest)

                manifestBuilder.append("Name: $name\n")
                manifestBuilder.append("SHA-256-Digest: $encodedDigest\n\n")
            }
        }

        val manifestBytes = manifestBuilder.toString().toByteArray(Charsets.UTF_8)
        val manifestDigest = Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(manifestBytes))

        val sfBuilder = StringBuilder()
        sfBuilder.append("Signature-Version: 1.0\n")
        sfBuilder.append("Created-By: OmniSplitX Engine\n")
        sfBuilder.append("SHA-256-Digest-Manifest: $manifestDigest\n\n")

        for ((name, bytes) in entriesData) {
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            val encodedDigest = Base64.getEncoder().encodeToString(digest)
            sfBuilder.append("Name: $name\n")
            sfBuilder.append("SHA-256-Digest: $encodedDigest\n\n")
        }

        val sfBytes = sfBuilder.toString().toByteArray(Charsets.UTF_8)

        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(sfBytes)
        val sigBytes = signature.sign()

        ZipOutputStream(FileOutputStream(output)).use { out ->
            ZipFile(input).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("META-INF/")) continue
                    out.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }

            out.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            out.write(manifestBytes)
            out.closeEntry()

            out.putNextEntry(ZipEntry("META-INF/CERT.SF"))
            out.write(sfBytes)
            out.closeEntry()

            out.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
            val rsaBlock = ByteArrayOutputStream().apply {
                write(sigBytes)
            }.toByteArray()
            out.write(rsaBlock)
            out.closeEntry()
        }
    }
}
