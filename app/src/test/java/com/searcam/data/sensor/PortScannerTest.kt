package com.searcam.data.sensor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PortScanner.isPrivateIp() 단위 테스트
 *
 * RFC 1918 사설 IP 범위 경계 값을 검증한다:
 *   10.0.0.0/8
 *   172.16.0.0/12 (172.16 ~ 172.31)
 *   192.168.0.0/16
 *   127.0.0.0/8 (루프백)
 */
class PortScannerTest {

    private lateinit var portScanner: PortScanner

    @Before
    fun setUp() {
        portScanner = PortScanner()
    }

    // ──────────────────────────────────────────────
    // 10.0.0.0/8 범위
    // ──────────────────────────────────────────────

    @Test
    fun `10 범위 시작 주소는 사설 IP`() {
        assertTrue(portScanner.isPrivateIp("10.0.0.0"))
    }

    @Test
    fun `10 범위 중간 주소는 사설 IP`() {
        assertTrue(portScanner.isPrivateIp("10.100.50.25"))
    }

    @Test
    fun `10 범위 끝 주소는 사설 IP`() {
        assertTrue(portScanner.isPrivateIp("10.255.255.255"))
    }

    @Test
    fun `9로 시작하는 주소는 사설 IP 아님`() {
        assertFalse(portScanner.isPrivateIp("9.255.255.255"))
    }

    @Test
    fun `11로 시작하는 주소는 사설 IP 아님`() {
        assertFalse(portScanner.isPrivateIp("11.0.0.0"))
    }

    // ──────────────────────────────────────────────
    // 172.16.0.0/12 범위 (172.16 ~ 172.31)
    // ──────────────────────────────────────────────

    @Test
    fun `172-16 범위 시작 주소는 사설 IP`() {
        assertTrue(portScanner.isPrivateIp("172.16.0.0"))
    }

    @Test
    fun `172-31 범위 끝 주소는 사설 IP`() {
        assertTrue(portScanner.isPrivateIp("172.31.255.255"))
    }

    @Test
    fun `172-16 이전 주소는 사설 IP 아님`() {
        assertFalse(portScanner.isPrivateIp("172.15.255.255"))
    }

    @Test
    fun `172-32 이후 주소는 사설 IP 아님`() {
        assertFalse(portScanner.isPrivateIp("172.32.0.0"))
    }

    @Test
    fun `172-20 중간 주소는 사설 IP`() {
        assertTrue(portScanner.isPrivateIp("172.20.100.1"))
    }

    // ──────────────────────────────────────────────
    // 192.168.0.0/16 범위
    // ──────────────────────────────────────────────

    @Test
    fun `192-168 범위 시작 주소는 사설 IP`() {
        assertTrue(portScanner.isPrivateIp("192.168.0.0"))
    }

    @Test
    fun `192-168 범위 끝 주소는 사설 IP`() {
        assertTrue(portScanner.isPrivateIp("192.168.255.255"))
    }

    @Test
    fun `공유기 기본 주소는 사설 IP`() {
        assertTrue(portScanner.isPrivateIp("192.168.1.1"))
    }

    @Test
    fun `192-167은 사설 IP 아님`() {
        assertFalse(portScanner.isPrivateIp("192.167.0.0"))
    }

    @Test
    fun `192-169는 사설 IP 아님`() {
        assertFalse(portScanner.isPrivateIp("192.169.0.0"))
    }

    // ──────────────────────────────────────────────
    // 127.0.0.0/8 루프백
    // ──────────────────────────────────────────────

    @Test
    fun `127-0-0-1 루프백은 사설 IP`() {
        assertTrue(portScanner.isPrivateIp("127.0.0.1"))
    }

    @Test
    fun `127 범위 전체는 사설 IP`() {
        assertTrue(portScanner.isPrivateIp("127.255.255.255"))
    }

    // ──────────────────────────────────────────────
    // 공인 IP (사설 IP 아님)
    // ──────────────────────────────────────────────

    @Test
    fun `구글 DNS 8-8-8-8은 사설 IP 아님`() {
        assertFalse(portScanner.isPrivateIp("8.8.8.8"))
    }

    @Test
    fun `공인 IP 1-1-1-1은 사설 IP 아님`() {
        assertFalse(portScanner.isPrivateIp("1.1.1.1"))
    }

    // ──────────────────────────────────────────────
    // 잘못된 입력
    // ──────────────────────────────────────────────

    @Test
    fun `문자열 입력은 사설 IP 아님`() {
        assertFalse(portScanner.isPrivateIp("not-an-ip"))
    }

    @Test
    fun `빈 문자열은 사설 IP 아님`() {
        assertFalse(portScanner.isPrivateIp(""))
    }

    @Test
    fun `옥텟이 4개 미만이면 사설 IP 아님`() {
        assertFalse(portScanner.isPrivateIp("192.168.1"))
    }

    @Test
    fun `옥텟이 4개 초과이면 사설 IP 아님`() {
        assertFalse(portScanner.isPrivateIp("192.168.1.1.1"))
    }
}
