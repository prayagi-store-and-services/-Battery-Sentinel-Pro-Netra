package com.example

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ai.BatteryDegradationPredictor
import com.example.model.BatteryTelemetry
import com.example.data.local.BatteryRecord
import com.example.data.local.ChargingSession
import com.example.util.BatteryPdfReportGenerator
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on real Android: Robolectric's PdfDocument cannot create a page. */
@RunWith(AndroidJUnit4::class)
class BatteryPdfExportTest {
    @Test
    fun realAndroidExportsReadablePdf() = verifyReport("report-unavailable", emptyList(), emptyList(), BatteryTelemetry())

    @Test
    fun realAndroidExportsObservedSessionPdf() = verifyReport(
        "report-observed",
        listOf(BatteryRecord(level = 80, temperature = 32f, voltageMv = 4100, currentMa = 1500, powerWatts = 6.15f, isCharging = true, pluggedType = "AC", healthStatus = "GOOD")),
        listOf(ChargingSession(startTime = 1000L, endTime = 2401000L, startLevel = 30, endLevel = 80, peakTemperature = 32f, avgPowerWatts = 7f, chargerType = "AC", durationMinutes = 40)),
        BatteryTelemetry(voltageMv = 4100, currentMa = 1500, isDataAvailable = true)
    )

    private fun verifyReport(name: String, records: List<BatteryRecord>, sessions: List<ChargingSession>, telemetry: BatteryTelemetry) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = BatteryDegradationPredictor.analyzeDegradationAndFailureRisk(records, sessions)
        val pdf = BatteryPdfReportGenerator.generateDailyReport(context, records, sessions, report, telemetry)
        assertTrue("Real Android must produce PDF bytes", pdf != null && pdf.length() > 100L)
        val output = File(context.getExternalFilesDir(null), "pdf-verification").apply { mkdirs() }
        pdf!!.copyTo(File(output, "$name.pdf"), overwrite = true)
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                assertEquals(1, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(output, "$name.png").outputStream().use {
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    bitmap.recycle()
                }
            }
        }
    }
}
