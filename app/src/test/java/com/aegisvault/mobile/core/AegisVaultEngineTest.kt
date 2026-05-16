package com.aegisvault.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AegisVaultEngineTest {
    @Test fun encryptDecryptRoundTrip() { val t = AegisVaultEngine.encryptText("hello", "pass12345"); assertEquals("hello", AegisVaultEngine.decryptText(t, "pass12345").plaintext) }
    @Test fun sameInputDifferentCiphertext() { val a = AegisVaultEngine.encryptText("hello", "pass12345"); val b = AegisVaultEngine.encryptText("hello", "pass12345"); assertNotEquals(a, b) }
    @Test fun wrongPasswordAuth() { val t = AegisVaultEngine.encryptText("hello", "pass12345"); try { AegisVaultEngine.decryptText(t, "bad") } catch (e:AegisVaultException){ assertEquals(ErrorCode.AUTH, e.code); return }; throw AssertionError() }
    @Test fun tamperedTokenFails() { val t = AegisVaultEngine.encryptText("hello", "pass12345"); val bad=t.dropLast(2)+"aa"; try { AegisVaultEngine.decryptText(bad, "pass12345") } catch (e:AegisVaultException){ assertTrue(e.code==ErrorCode.AUTH || e.code==ErrorCode.PROTOCOL); return }; throw AssertionError() }
    @Test fun emptyTextAndPassword() { try { AegisVaultEngine.encryptText("", "x") } catch (e:AegisVaultException){ assertEquals(ErrorCode.EMPTY_TEXT, e.code) }; try { AegisVaultEngine.encryptText("x", "") } catch (e:AegisVaultException){ assertEquals(ErrorCode.EMPTY_PASSWORD, e.code) } }
    @Test fun invalidUtf8Base64() { try { AegisVaultEngine.decodeText("gA==") } catch (e:AegisVaultException){ assertEquals(ErrorCode.UTF8_INVALID, e.code); return }; throw AssertionError() }
    @Test fun legacyAkMode() {
        val nonce = ByteArray(12) { 1 }
        val key = java.security.MessageDigest.getInstance("SHA-256").digest("legacy-pass".toByteArray())
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, nonce))
        val ct = cipher.doFinal("legacy".toByteArray())
        val token = java.util.Base64.getEncoder().encodeToString(nonce + ct)
        val out = AegisVaultEngine.decryptText("AK#legacy-pass#$token", "")
        assertTrue(out.usedLegacyAk)
        assertEquals("legacy", out.plaintext)
    }
}
