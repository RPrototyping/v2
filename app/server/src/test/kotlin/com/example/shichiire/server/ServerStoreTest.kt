package com.example.shichiire.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerStoreTest {
    private fun storeWithPromise(): ServerStore {
        val s = ServerStore(clock = { 1_000_000L })
        assertNull(s.create(CreateRequest("p1", "遅刻しない", "けんた", "たけし", 2_000_000L, "ひみつ")))
        return s
    }

    private fun ServerStore.view(me: String) = visibleTo(me).single()

    /** 「自分だけ」の約束では、約束した人について答える */
    private fun ServerStore.report(id: String, by: String, kept: Boolean) = report(id, by, kept, null)

    @Test
    fun 両方守ったなら燃えて中身も消える() {
        val s = storeWithPromise()
        assertEquals(ReportResult.OK, s.report("p1", "けんた", true))
        assertEquals(ReportResult.OK, s.report("p1", "たけし", true))
        val v = s.view("たけし")
        assertEquals("KEPT", v.status)
        assertEquals("", v.selfCollateral)
    }

    @Test
    fun 両方破ったなら担保が相手に見える() {
        val s = storeWithPromise()
        s.report("p1", "たけし", false) // 相手が先でもよい
        s.report("p1", "けんた", false)
        assertEquals("BROKEN", s.view("たけし").status)
        assertEquals("ひみつ", s.view("たけし").selfCollateral)
    }

    @Test
    fun 食い違ったら不一致で担保は永久に封印() {
        val s = storeWithPromise()
        s.report("p1", "けんた", true)
        s.report("p1", "たけし", false)
        val v = s.view("けんた")
        assertEquals("MISMATCH", v.status)
        assertEquals("", v.selfCollateral)
        assertEquals(true, v.creatorSaysCreator)
        assertEquals(false, v.partnerSaysCreator)
    }

    @Test
    fun 結果が出るまで相手の答えと担保は見えない() {
        val s = storeWithPromise()
        s.report("p1", "けんた", false)
        val partnerView = s.view("たけし")
        assertTrue(partnerView.selfReported)
        assertNull(partnerView.creatorSaysCreator)
        assertEquals("", partnerView.selfCollateral)
        val creatorView = s.view("けんた")
        assertEquals(false, creatorView.creatorSaysCreator)
        assertEquals("", creatorView.selfCollateral)
    }

    @Test
    fun 二重申告と部外者は弾く() {
        val s = storeWithPromise()
        assertEquals(ReportResult.OK, s.report("p1", "けんた", true))
        assertEquals(ReportResult.ALREADY, s.report("p1", "けんた", false))
        assertEquals(ReportResult.NOT_MEMBER, s.report("p1", "みさき", true))
        assertEquals(ReportResult.NOT_FOUND, s.report("zzz", "けんた", true))
        assertTrue(s.visibleTo("みさき").isEmpty())
    }

    @Test
    fun 自分とは約束できない() {
        val s = ServerStore()
        assertNotNull(s.create(CreateRequest("p2", "x", "けんた", "けんた", 0L, "y")))
    }

    @Test
    fun 期限が過ぎた約束は作れない() {
        val s = ServerStore(clock = { 1_000_000L })
        assertNotNull(s.create(CreateRequest("p3", "x", "けんた", "たけし", 0L, "y")))
        assertNull(s.create(CreateRequest("p4", "x", "けんた", "たけし", 1_000_000L - 30_000L, "y"))) // 時計のずれの範囲内
        assertNull(s.create(CreateRequest("p5", "x", "けんた", "たけし", 1_000_000L - 60_000L, "y"))) // ちょうど1分は許す
        assertNotNull(s.create(CreateRequest("p6", "x", "けんた", "たけし", 1_000_000L - 60_001L, "y")))
    }
}
