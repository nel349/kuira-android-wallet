package com.midnight.kuira.core.indexer.api

/**
 * TLS/SSL configuration for secure indexer communication.
 *
 * **CRITICAL SECURITY REQUIREMENT:**
 * - Production MUST use HTTPS with certificate pinning
 * - HTTP only allowed for localhost development testing
 * - Certificate pinning prevents MITM attacks
 *
 * **Certificate Pinning Implementation (Phase 4B):**
 *
 * Option 1: OkHttp Engine (Recommended)
 * ```kotlin
 * val client = HttpClient(OkHttp) {
 *     engine {
 *         config {
 *             certificatePinner(CertificatePinner.Builder()
 *                 .add("indexer.midnight.network", "sha256/AAAAAAAAAAAAA...")
 *                 .add("indexer.midnight.network", "sha256/BBBBBBBBBBBBB...") // Backup cert
 *                 .build()
 *             )
 *         }
 *     }
 * }
 * ```
 *
 * Option 2: Android Network Security Config (App-Level)
 * ```xml
 * <!-- res/xml/network_security_config.xml -->
 * <network-security-config>
 *     <domain-config>
 *         <domain includeSubdomains="true">indexer.midnight.network</domain>
 *         <pin-set expiration="2027-01-01">
 *             <pin digest="SHA-256">AAAAAAAAAAAAA...</pin>
 *             <pin digest="SHA-256">BBBBBBBBBBBBB...</pin>
 *         </pin-set>
 *     </domain-config>
 * </network-security-config>
 * ```
 *
 * Option 3: Custom TrustManager (Most Control)
 * ```kotlin
 * class PinnedCertificateTrustManager(
 *     private val pinnedCertificates: List<String>
 * ) : X509TrustManager {
 *     override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
 *         val serverCertHash = chain[0].publicKey.encoded.sha256()
 *         if (!pinnedCertificates.contains(serverCertHash)) {
 *             throw CertificateException("Certificate pin validation failed")
 *         }
 *     }
 * }
 * ```
 *
 * **How to Get Certificate Fingerprints:**
 *
 * 1. Using OpenSSL:
 * ```bash
 * openssl s_client -connect indexer.midnight.network:443 < /dev/null | \
 *   openssl x509 -pubkey -noout | \
 *   openssl rsa -pubin -outform der | \
 *   openssl dgst -sha256 -binary | \
 *   openssl enc -base64
 * ```
 *
 * 2. Using Chrome DevTools:
 * - Visit https://indexer.midnight.network
 * - Click padlock → Certificate → Details → Copy SHA-256 fingerprint
 *
 * 3. Using curl:
 * ```bash
 * curl --pinnedpubkey 'sha256//AAAAAAAAAA...' https://indexer.midnight.network
 * ```
 *
 * **Best Practices:**
 * - Pin at least 2 certificates (primary + backup)
 * - Update pins before certificates expire
 * - Use public key pinning (not leaf certificate)
 * - Test pinning in staging before production
 * - Have a backup plan if pinning fails (e.g., app update)
 *
 * **Testing Certificate Pinning:**
 * - Use mitmproxy or Charles Proxy to verify MITM protection
 * - Ensure app rejects connections with invalid certificates
 * - Test certificate rotation scenarios
 */
data class TlsConfiguration(
    /**
     * List of SHA-256 public key fingerprints (base64-encoded).
     *
     * Example: ["AAAAAAAAAAAAA...", "BBBBBBBBBBBBB..."]
     */
    val pinnedCertificates: List<String> = emptyList(),

    /**
     * Allow HTTP connections to localhost (INSECURE - testing only).
     *
     * **NEVER enable in production builds.**
     */
    val allowLocalhostHttp: Boolean = false,

    /**
     * Minimum TLS version.
     *
     * **Default:** TLS 1.2
     * **Recommended:** TLS 1.3 when widely supported
     */
    val minTlsVersion: TlsVersion = TlsVersion.TLS_1_2
) {
    init {
        require(!allowLocalhostHttp || pinnedCertificates.isEmpty()) {
            "Certificate pinning not compatible with localhost HTTP mode"
        }
    }
}

/**
 * TLS protocol versions.
 */
enum class TlsVersion {
    TLS_1_2,
    TLS_1_3
}

/**
 * Certificate pinning exception.
 *
 * Thrown when server certificate doesn't match pinned certificates.
 */
class CertificatePinningException(
    message: String,
    cause: Throwable? = null
) : IndexerException(message, cause)
