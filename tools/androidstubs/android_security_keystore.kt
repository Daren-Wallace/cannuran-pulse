@file:Suppress("unused", "UNUSED_PARAMETER")
package android.security.keystore

object KeyProperties { const val PURPOSE_ENCRYPT = 1; const val PURPOSE_DECRYPT = 2; const val KEY_ALGORITHM_AES = "AES"
  const val BLOCK_MODE_GCM = "GCM"; const val ENCRYPTION_PADDING_NONE = "NoPadding" }
class KeyGenParameterSpec : java.security.spec.AlgorithmParameterSpec {
  class Builder(alias: String, purposes: Int) {
    fun setBlockModes(vararg m: String): Builder = this
    fun setEncryptionPaddings(vararg p: String): Builder = this
    fun setKeySize(s: Int): Builder = this
    fun build(): KeyGenParameterSpec = KeyGenParameterSpec()
  }
}
