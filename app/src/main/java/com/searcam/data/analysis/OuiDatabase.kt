package com.searcam.data.analysis

import android.content.Context
import com.searcam.util.Constants
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

/**
 * OUI(Organizationally Unique Identifier) 데이터베이스
 *
 * MAC 주소 앞 3바이트(OUI)로 네트워크 기기 제조사를 식별한다.
 * assets/oui.json (약 500KB)에서 로드한다.
 *
 * 비유: 자동차 번호판의 지역 코드처럼, MAC 주소 앞 6자리로
 * 네트워크 기기를 만든 회사를 알 수 있다.
 *
 * 초기화 비용이 크므로 @Singleton으로 한 번만 로드한다.
 */
class OuiDatabase @Inject constructor(
    private val context: Context
) {

    // MAC OUI → 제조사명 매핑 테이블 (불변 Map)
    private val ouiMap: Map<String, String> by lazy { loadOuiJson() }

    /**
     * MAC 주소로 제조사명을 조회한다.
     *
     * @param macAddress MAC 주소 (예: "AA:BB:CC:DD:EE:FF" 또는 "AA-BB-CC-DD-EE-FF")
     * @return 제조사명 또는 null (미등록 OUI인 경우)
     */
    fun getVendor(macAddress: String): String? {
        val oui = normalizeOui(macAddress) ?: return null
        return ouiMap[oui]
    }

    /**
     * 제조사명이 카메라 관련 키워드를 포함하는지 확인
     *
     * 알려진 카메라 제조사 OUI 목록에 해당하면 위험도 가중치를 올린다.
     *
     * @param macAddress 확인할 MAC 주소
     * @return 카메라 관련 제조사이면 true
     */
    fun isCameraVendor(macAddress: String): Boolean {
        val vendor = getVendor(macAddress)?.lowercase() ?: return false
        return CAMERA_VENDOR_KEYWORDS.any { keyword -> vendor.contains(keyword) }
    }

    /**
     * OUI JSON 파일을 assets에서 로드한다.
     *
     * 형식: { "AABBCC": "제조사명", ... }
     * 파일이 없거나 파싱 오류 시 빈 Map을 반환하고 에러 코드 E2005를 기록한다.
     */
    private fun loadOuiJson(): Map<String, String> {
        return try {
            val json = context.assets.open(Constants.OUI_JSON_ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }

            val jsonObject = JSONObject(json)
            val result = mutableMapOf<String, String>()

            jsonObject.keys().forEach { key ->
                result[key.uppercase()] = jsonObject.getString(key)
            }

            Timber.d("OUI DB 로드 완료: ${result.size}개 항목")
            result.toMap() // 불변 Map으로 변환
        } catch (e: Exception) {
            Timber.e(e, "[${Constants.ErrorCode.E2005}] OUI 데이터베이스 로드 실패")
            emptyMap()
        }
    }

    /**
     * MAC 주소에서 OUI 부분(앞 6자리)을 정규화한다.
     *
     * "AA:BB:CC:DD:EE:FF" → "AABBCC"
     * "AA-BB-CC-DD-EE-FF" → "AABBCC"
     *
     * @param macAddress 정규화할 MAC 주소
     * @return 대문자 6자리 OUI 또는 null (유효하지 않은 형식)
     */
    private fun normalizeOui(macAddress: String): String? {
        val cleaned = macAddress.replace(":", "").replace("-", "").uppercase()
        return if (cleaned.length >= 6) cleaned.take(6) else null
    }

    /**
     * MAC 주소로 제조사명을 조회한다 (lookupVendor 별칭).
     *
     * @param macAddress MAC 주소 (예: "AA:BB:CC:DD:EE:FF")
     * @return 제조사명 또는 null (미등록 OUI인 경우)
     */
    fun lookupVendor(macAddress: String): String? = getVendor(macAddress)

    companion object {
        // 카메라 제조사 식별 키워드 (소문자)
        private val CAMERA_VENDOR_KEYWORDS = listOf(
            "hikvision", "dahua", "axis", "hanwha", "bosch",
            "pelco", "vivotek", "reolink", "amcrest", "foscam",
            "eufy", "wyze", "arlo", "ring", "nest",
            "xiaomi", "tuya", "vstarcam", "tenvis"
        )
    }
}
