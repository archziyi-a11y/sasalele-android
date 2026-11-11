package com.example.sasalele
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sasalele.data.CaptureFrameGate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class CaptureFrameGateTest {
 @Test fun transientBlankThenValidFrame() {
  val gate=CaptureFrameGate()
  assertEquals(CaptureFrameGate.Decision.WAIT,gate.inspect(true,100))
  assertEquals(CaptureFrameGate.Decision.WAIT,gate.inspect(true,200))
  assertEquals(CaptureFrameGate.Decision.ACCEPT,gate.inspect(false,500))
 }
 @Test fun nextVsyncValidFrameMustNotBeDropped() {
  val gate=CaptureFrameGate()
  assertEquals(CaptureFrameGate.Decision.WAIT,gate.inspect(true,100))
  assertEquals(CaptureFrameGate.Decision.ACCEPT,gate.inspect(false,116))
 }
 @Test fun persistentBlankStopsAtBound() {
  val gate=CaptureFrameGate()
  assertEquals(CaptureFrameGate.Decision.WAIT,gate.inspect(true,100))
  assertFalse(gate.deadlineReached(3099))
  assertTrue(gate.deadlineReached(3100))
  assertEquals(CaptureFrameGate.Decision.EMPTY_TIMEOUT,gate.inspect(true,3100))
 }
 @Test fun noFurtherFrameStillHasDeadlineAndNewRequestIsIndependent() {
  val first=CaptureFrameGate();first.inspect(true,0)
  assertTrue(first.deadlineReached(3000))
  val next=CaptureFrameGate()
  assertFalse(next.deadlineReached(3000))
  assertEquals(CaptureFrameGate.Decision.WAIT,next.inspect(true,3000))
 }
}
