package com.example.shichiire.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 担保を出す人（自分だけ／相手だけ／2人とも）と、人ごとの判定まわり */
class HolderTest {
    private fun store() = ServerStore(clock = { 1_000_000L })
    private fun req(id: String, holder: String, collateral: String = "けんたの担保") =
        CreateRequest(id, "週末に水族館", "けんた", "たけし", 2_000_000L, collateral, holder)

    @Test
    fun 相手だけなら本人の担保はいらず相手が封をするまで申告できない() {
        val s = store()
        assertNull(s.create(req("p1", "PARTNER", collateral = "")))
        assertEquals(ReportResult.NOT_READY, s.report("p1", "けんた", null, true))
        assertEquals(SealResult.NOT_PARTNER, s.sealPartner("p1", "けんた", "x"))
        assertEquals(SealResult.OK, s.sealPartner("p1", "たけし", "たけしの担保"))
        assertEquals(SealResult.ALREADY, s.sealPartner("p1", "たけし", "もう一回"))
        val v = s.visibleTo("けんた").single()
        assertEquals("", v.partnerCollateral) // 封をした中身は見えない
        assertEquals(true, v.partnerSealed)
        // 相手について答えていなければ受け付けない
        assertEquals(ReportResult.INCOMPLETE, s.report("p1", "けんた", true, null))
        assertEquals(ReportResult.OK, s.report("p1", "けんた", null, false))
        assertEquals(ReportResult.OK, s.report("p1", "たけし", null, false))
        val after = s.visibleTo("けんた").single()
        assertEquals("BROKEN", after.status)
        assertEquals("BROKEN", after.partnerOutcome)
        assertNull(after.creatorOutcome)
        assertEquals("たけしの担保", after.partnerCollateral)
    }

    @Test
    fun 二人ともで片方だけ破ったら破った人の担保だけ公開される() {
        val s = store()
        assertNull(s.create(req("p2", "BOTH")))
        s.sealPartner("p2", "たけし", "たけしの担保")
        assertEquals(ReportResult.INCOMPLETE, s.report("p2", "けんた", true, null)) // 2人分答える
        // けんたは守った、たけしは破った（2人とも正直に答えた）
        assertEquals(ReportResult.OK, s.report("p2", "けんた", true, false))
        assertEquals(ReportResult.OK, s.report("p2", "たけし", true, false))
        val v = s.visibleTo("けんた").single()
        assertEquals("MIXED", v.status)
        assertEquals("KEPT", v.creatorOutcome)
        assertEquals("BROKEN", v.partnerOutcome)
        assertEquals("", v.selfCollateral) // けんたの担保は燃えた
        assertEquals("たけしの担保", v.partnerCollateral) // たけしの担保だけ公開
    }

    @Test
    fun 二人とも破ったなら両方の担保が開く() {
        val s = store()
        s.create(req("p3", "BOTH"))
        s.sealPartner("p3", "たけし", "たけしの担保")
        s.report("p3", "けんた", false, false)
        s.report("p3", "たけし", false, false)
        val v = s.visibleTo("たけし").single()
        assertEquals("BROKEN", v.status)
        assertEquals("けんたの担保", v.selfCollateral)
        assertEquals("たけしの担保", v.partnerCollateral)
    }

    @Test
    fun 二人ともで一人分だけ食い違ったらその人の担保だけ永久封印() {
        val s = store()
        s.create(req("p4", "BOTH"))
        s.sealPartner("p4", "たけし", "たけしの担保")
        // けんたについて：けんた「守った」／たけし「破った」→ 不一致。たけしについて：2人とも「破った」
        s.report("p4", "けんた", true, false)
        s.report("p4", "たけし", false, false)
        val v = s.visibleTo("けんた").single()
        assertEquals("MIXED", v.status)
        assertEquals("MISMATCH", v.creatorOutcome)
        assertEquals("BROKEN", v.partnerOutcome)
        assertEquals("", v.selfCollateral)
        assertEquals("たけしの担保", v.partnerCollateral)
    }

    @Test
    fun 結果が出るまで相手の答えは見えない() {
        val s = store()
        s.create(req("p5", "BOTH"))
        s.sealPartner("p5", "たけし", "たけしの担保")
        s.report("p5", "けんた", true, false)
        val partnerView = s.visibleTo("たけし").single()
        assertNull(partnerView.creatorSaysCreator)
        assertNull(partnerView.creatorSaysPartner)
        val creatorView = s.visibleTo("けんた").single()
        assertEquals(true, creatorView.creatorSaysCreator)
        assertEquals(false, creatorView.creatorSaysPartner)
    }

    @Test
    fun 二人とも守ったなら両方燃える() {
        val s = store()
        s.create(req("p6", "BOTH"))
        s.sealPartner("p6", "たけし", "たけしの担保")
        s.report("p6", "けんた", true, true)
        s.report("p6", "たけし", true, true)
        val v = s.visibleTo("けんた").single()
        assertEquals("KEPT", v.status)
        assertEquals("", v.selfCollateral)
        assertEquals("", v.partnerCollateral)
    }

    @Test
    fun 自分だけなら相手の担保は受け付けず_相手についての答えは捨てる() {
        val s = store()
        s.create(req("p7", "SELF"))
        assertEquals(SealResult.NOT_NEEDED, s.sealPartner("p7", "たけし", "x"))
        assertEquals(ReportResult.OK, s.report("p7", "たけし", true, false))
        assertNull(s.visibleTo("たけし").single().partnerSaysPartner)
    }

    @Test
    fun 本人が出すのに担保が空なら作れない_出し手の指定が変でも作れない() {
        val s = store()
        assertNotNull(s.create(req("p8", "BOTH", collateral = " ")))
        assertNotNull(s.create(req("p9", "NOBODY")))
    }
}
