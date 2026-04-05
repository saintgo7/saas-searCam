package com.searcam.data.remote

import com.searcam.domain.model.LayerResult
import com.searcam.domain.model.LayerType
import com.searcam.domain.model.MagneticReading
import com.searcam.domain.model.NetworkDevice
import com.searcam.domain.model.ScanReport
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * ScanReport를 cam.abada.co.kr API 서버로 업로드한다.
 *
 * 비유: 진단 결과지를 클라우드 의료 시스템에 자동 전송하는 것처럼,
 * 스캔 완료 후 결과를 서버로 보내 웹에서도 볼 수 있게 한다.
 *
 * - 실패해도 앱 정상 동작에 영향 없음 (fire-and-forget)
 * - 타임아웃 10초 (네트워크 불안정 대비)
 */
@Singleton
class ScanReportUploader @Inject constructor(
    @Named("reportApiUrl") private val apiUrl: String,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * ScanReport를 서버에 POST 전송한다.
     *
     * 실패 시 오류를 로깅하고 조용히 종료한다.
     * 앱의 핵심 스캔 기능과 독립적으로 동작한다.
     *
     * @param report 업로드할 스캔 결과
     */
    fun upload(report: ScanReport) {
        try {
            val json = toJson(report)
            val body = json.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$apiUrl/api/report")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Timber.d("ScanReport 업로드 성공: id=${report.id}, score=${report.riskScore}")
                } else {
                    Timber.w("ScanReport 업로드 실패: HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "ScanReport 업로드 오류 (앱 동작에 영향 없음)")
        }
    }

    /**
     * ScanReport를 웹에서 파싱 가능한 JSON 문자열로 직렬화한다.
     *
     * Gson 미사용 — 의존성 최소화를 위해 org.json으로 직접 구성한다.
     */
    private fun toJson(report: ScanReport): String {
        return JSONObject().apply {
            put("id", report.id)
            put("mode", report.mode.name)
            put("startedAt", report.startedAt)
            put("completedAt", report.completedAt)
            put("riskScore", report.riskScore)
            put("riskLevel", report.riskLevel.name)
            put("correctionFactor", report.correctionFactor)
            put("locationNote", report.locationNote)
            put("durationMs", report.durationMs)
            put("suspiciousDeviceCount", report.suspiciousDeviceCount)
            put("devices", devicesToJson(report.devices))
            put("layerResults", layerResultsToJson(report.layerResults))
            put("magneticReadings", magneticReadingsToJson(report.magneticReadings))
        }.toString()
    }

    private fun devicesToJson(devices: List<NetworkDevice>): JSONArray {
        return JSONArray().apply {
            devices.forEach { d ->
                put(JSONObject().apply {
                    put("ip", d.ip)
                    put("mac", d.mac)
                    put("hostname", d.hostname ?: JSONObject.NULL)
                    put("vendor", d.vendor ?: JSONObject.NULL)
                    put("deviceType", d.deviceType.name)
                    put("riskScore", d.riskScore)
                    put("isCamera", d.isCamera)
                    put("discoveryMethod", d.discoveryMethod.name)
                    put("services", JSONArray(d.services))
                    put("openPorts", JSONArray(d.openPorts))
                })
            }
        }
    }

    private fun layerResultsToJson(results: Map<LayerType, LayerResult>): JSONObject {
        return JSONObject().apply {
            results.forEach { (type, result) ->
                put(type.name, JSONObject().apply {
                    put("status", result.status.name)
                    put("score", result.score)
                    put("durationMs", result.durationMs)
                    put("isPositive", result.isPositive)
                    put("weight", type.weight)
                    put("weightNoWifi", type.weightNoWifi)
                    put("labelKo", type.labelKo)
                })
            }
        }
    }

    private fun magneticReadingsToJson(readings: List<MagneticReading>): JSONArray {
        return JSONArray().apply {
            readings.forEach { r ->
                put(JSONObject().apply {
                    put("timestamp", r.timestamp)
                    put("x", r.x)
                    put("y", r.y)
                    put("z", r.z)
                    put("magnitude", r.magnitude)
                    put("delta", r.delta)
                    put("level", r.level.name)
                })
            }
        }
    }
}
