package com.pocketmanager.app
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Backup {
    private val header="PMB1".toByteArray(Charsets.US_ASCII)
    private fun key(password:CharArray,salt:ByteArray):SecretKeySpec {val spec=PBEKeySpec(password,salt,250_000,256);return try{SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES")}finally{spec.clearPassword()}}
    fun encrypt(plain:ByteArray,password:CharArray):ByteArray {require(password.size>=12);val r=SecureRandom();val salt=ByteArray(16).also(r::nextBytes);val iv=ByteArray(12).also(r::nextBytes);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(password,salt),GCMParameterSpec(128,iv));c.updateAAD(header);return header+salt+iv+c.doFinal(plain)}
    fun decrypt(data:ByteArray,password:CharArray):ByteArray {require(data.size>=48&&data.copyOfRange(0,4).contentEquals(header));val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(password,data.copyOfRange(4,20)),GCMParameterSpec(128,data.copyOfRange(20,32)));c.updateAAD(header);return c.doFinal(data.copyOfRange(32,data.size))}
}
