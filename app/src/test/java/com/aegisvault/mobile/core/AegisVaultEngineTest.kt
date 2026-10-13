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
    @Test fun emptyTextIsValidButEmptyPasswordFailsExplicitly() {
        assertEquals("", AegisVaultEngine.decryptText(AegisVaultEngine.encryptText("", "x"), "x").plaintext)
        val passwordError = org.junit.Assert.assertThrows(AegisVaultException::class.java) { AegisVaultEngine.encryptText("x", "") }
        assertEquals(ErrorCode.EMPTY_PASSWORD, passwordError.code)
    }
    @Test fun invalidUtf8Base64() { try { AegisVaultEngine.decodeText("gA==") } catch (e:AegisVaultException){ assertEquals(ErrorCode.UTF8_INVALID, e.code); return }; throw AssertionError() }

    @Test fun invalidBase64SyntaxFails() { try { AegisVaultEngine.decodeText("%%%") } catch (e:AegisVaultException){ assertEquals(ErrorCode.BASE64_INVALID, e.code); return }; throw AssertionError() }
    @Test fun unsupportedVersionFails() { try { AegisVaultEngine.decryptText("AGV9.invalid", "pass12345") } catch (e:AegisVaultException){ assertEquals(ErrorCode.PROTOCOL, e.code); return }; throw AssertionError() }
    @Test fun legacyAkRequiresMigration() {
        val sample = org.json.JSONObject(javaClass.getResource("/android-historical.json")!!.readText()).getJSONObject("ak")
        val e = org.junit.Assert.assertThrows(AegisVaultException::class.java) {
            AegisVaultEngine.decryptText(sample.getString("token"), "")
        }
        assertEquals(ErrorCode.MIGRATION, e.code)
    }
}
