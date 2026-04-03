package com.searcam.data.local.converter

import androidx.room.TypeConverter
import com.searcam.domain.model.LayerType
import com.searcam.domain.model.RiskLevel
import com.searcam.domain.model.ScanMode
import timber.log.Timber

/**
 * Room TypeConverter 모음
 *
 * Room은 기본 타입(Int, String, Long 등)만 저장 가능하므로,
 * 복합 타입을 String으로 직렬화/역직렬화하는 변환기를 제공한다.
 *
 * Gson 의존성 없이 간단한 JSON 형식을 직접 파싱한다.
 * 구조가 복잡해질 경우 Gson/Moshi TypeToken을 사용하도록 교체 가능하다.
 *
 * 적용 대상:
 *   - List<String>  ↔ JSON String  (예: ["a","b","c"])
 *   - List<Int>     ↔ JSON String  (예: [1,2,3])
 *   - RiskLevel     ↔ String       (enum name)
 *   - ScanMode      ↔ String       (enum name)
 *   - LayerType     ↔ String       (enum name)
 */
class SearCamTypeConverters {

    // ─────────────────────────────────────────────────────────
    // List<String> ↔ JSON String
    // ─────────────────────────────────────────────────────────

    /**
     * List<String>을 JSON 배열 문자열로 직렬화한다.
     *
     * 예: ["서울","부산"] → `["서울","부산"]`
     * null 또는 빈 리스트는 "[]"로 직렬화한다.
     *
     * @param value 변환할 문자열 목록
     * @return JSON 배열 문자열
     */
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        if (value.isNullOrEmpty()) return "[]"
        return value.joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]",
            transform = { item -> "\"${item.replace("\"", "\\\"")}\"" },
        )
    }

    /**
     * JSON 배열 문자열을 List<String>으로 역직렬화한다.
     *
     * 예: `["서울","부산"]` → ["서울","부산"]
     * 파싱 실패 시 빈 리스트를 반환하고 오류를 로깅한다.
     *
     * @param value JSON 배열 문자열
     * @return 문자열 목록
     */
    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank() || value.trim() == "[]") return emptyList()

        return try {
            val trimmed = value.trim().removePrefix("[").removeSuffix("]")
            if (trimmed.isBlank()) return emptyList()

            // CSV 파싱: 큰따옴표로 감싸진 항목을 분리
            parseStringArray(trimmed)
        } catch (e: Exception) {
            Timber.e(e, "TypeConverter: List<String> 파싱 실패 — value=$value")
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────
    // List<Int> ↔ JSON String
    // ─────────────────────────────────────────────────────────

    /**
     * List<Int>를 JSON 배열 문자열로 직렬화한다.
     *
     * 예: [1,2,3] → `[1,2,3]`
     *
     * @param value 변환할 정수 목록
     * @return JSON 배열 문자열
     */
    @TypeConverter
    fun fromIntList(value: List<Int>?): String {
        if (value.isNullOrEmpty()) return "[]"
        return value.joinToString(separator = ",", prefix = "[", postfix = "]")
    }

    /**
     * JSON 배열 문자열을 List<Int>로 역직렬화한다.
     *
     * 예: `[1,2,3]` → [1,2,3]
     * 파싱 실패 시 빈 리스트를 반환하고 오류를 로깅한다.
     *
     * @param value JSON 배열 문자열
     * @return 정수 목록
     */
    @TypeConverter
    fun toIntList(value: String?): List<Int> {
        if (value.isNullOrBlank() || value.trim() == "[]") return emptyList()

        return try {
            val trimmed = value.trim().removePrefix("[").removeSuffix("]")
            if (trimmed.isBlank()) return emptyList()

            trimmed.split(",").mapNotNull { it.trim().toIntOrNull() }
        } catch (e: Exception) {
            Timber.e(e, "TypeConverter: List<Int> 파싱 실패 — value=$value")
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────
    // RiskLevel ↔ String
    // ─────────────────────────────────────────────────────────

    /**
     * RiskLevel enum을 String(name)으로 변환한다.
     *
     * 예: RiskLevel.DANGER → "DANGER"
     * null 입력 시 기본값 "SAFE" 반환.
     *
     * @param value 변환할 RiskLevel
     * @return enum name 문자열
     */
    @TypeConverter
    fun fromRiskLevel(value: RiskLevel?): String {
        return value?.name ?: RiskLevel.SAFE.name
    }

    /**
     * String(name)을 RiskLevel enum으로 변환한다.
     *
     * 매핑 실패 시 RiskLevel.SAFE를 반환하고 오류를 로깅한다.
     *
     * @param value enum name 문자열
     * @return 해당하는 RiskLevel
     */
    @TypeConverter
    fun toRiskLevel(value: String?): RiskLevel {
        if (value.isNullOrBlank()) return RiskLevel.SAFE

        return try {
            RiskLevel.valueOf(value)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "TypeConverter: 알 수 없는 RiskLevel — value=$value, 기본값 SAFE 반환")
            RiskLevel.SAFE
        }
    }

    // ─────────────────────────────────────────────────────────
    // ScanMode ↔ String
    // ─────────────────────────────────────────────────────────

    /**
     * ScanMode enum을 String(name)으로 변환한다.
     *
     * 예: ScanMode.QUICK → "QUICK"
     *
     * @param value 변환할 ScanMode
     * @return enum name 문자열
     */
    @TypeConverter
    fun fromScanMode(value: ScanMode?): String {
        return value?.name ?: ScanMode.QUICK.name
    }

    /**
     * String(name)을 ScanMode enum으로 변환한다.
     *
     * 매핑 실패 시 ScanMode.QUICK을 반환하고 오류를 로깅한다.
     *
     * @param value enum name 문자열
     * @return 해당하는 ScanMode
     */
    @TypeConverter
    fun toScanMode(value: String?): ScanMode {
        if (value.isNullOrBlank()) return ScanMode.QUICK

        return try {
            ScanMode.valueOf(value)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "TypeConverter: 알 수 없는 ScanMode — value=$value, 기본값 QUICK 반환")
            ScanMode.QUICK
        }
    }

    // ─────────────────────────────────────────────────────────
    // LayerType ↔ String
    // ─────────────────────────────────────────────────────────

    /**
     * LayerType enum을 String(name)으로 변환한다.
     *
     * 예: LayerType.LENS → "LENS"
     *
     * @param value 변환할 LayerType
     * @return enum name 문자열
     */
    @TypeConverter
    fun fromLayerType(value: LayerType?): String {
        return value?.name ?: LayerType.WIFI.name
    }

    /**
     * String(name)을 LayerType enum으로 변환한다.
     *
     * 매핑 실패 시 LayerType.WIFI를 반환하고 오류를 로깅한다.
     *
     * @param value enum name 문자열
     * @return 해당하는 LayerType
     */
    @TypeConverter
    fun toLayerType(value: String?): LayerType {
        if (value.isNullOrBlank()) return LayerType.WIFI

        return try {
            LayerType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "TypeConverter: 알 수 없는 LayerType — value=$value, 기본값 WIFI 반환")
            LayerType.WIFI
        }
    }

    // ─────────────────────────────────────────────────────────
    // Long timestamp ↔ 표준 Long (현재는 직접 저장 가능하므로 참조용)
    // ─────────────────────────────────────────────────────────

    /**
     * Long 타임스탬프를 그대로 반환한다.
     *
     * Room은 Long을 직접 저장할 수 있지만,
     * java.util.Date 등 변환이 필요한 경우 이 컨버터를 확장한다.
     *
     * @param value Unix epoch millis
     * @return 동일한 Long 값
     */
    @TypeConverter
    fun fromTimestamp(value: Long?): Long {
        return value ?: 0L
    }

    /**
     * Long 타임스탬프를 그대로 반환한다.
     *
     * @param value Unix epoch millis
     * @return 동일한 Long 값
     */
    @TypeConverter
    fun toTimestamp(value: Long?): Long {
        return value ?: 0L
    }

    // ─────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────────────────

    /**
     * 큰따옴표로 감싸진 JSON 문자열 배열을 파싱한다.
     *
     * `"a","b","c"` 형식을 ["a", "b", "c"]로 변환한다.
     * 이스케이프된 따옴표(`\"`)를 올바르게 처리한다.
     *
     * @param input 배열 괄호를 제거한 JSON 내부 문자열
     * @return 파싱된 문자열 목록
     */
    private fun parseStringArray(input: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0

        while (i < input.length) {
            // 따옴표로 시작하는 항목 파싱
            if (input[i] == '"') {
                val sb = StringBuilder()
                i++ // 여는 따옴표 건너뜀
                while (i < input.length && input[i] != '"') {
                    if (input[i] == '\\' && i + 1 < input.length) {
                        i++ // 이스케이프 문자 건너뜀
                        sb.append(input[i])
                    } else {
                        sb.append(input[i])
                    }
                    i++
                }
                result.add(sb.toString())
                i++ // 닫는 따옴표 건너뜀
            } else {
                i++
            }
        }

        return result
    }
}
