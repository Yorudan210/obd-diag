package com.obddiag.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdParserTest {

    private fun msgs(raw: String) = ObdParser.parseMessages(ObdParser.cleanLines(raw))

    @Test fun singleFrameRpm() {
        val m = msgs("41 0C 1A F8 \r\r")
        val data = ObdParser.pidData(m, 0x0C)!!
        val rpm = PidCatalog.byPid[0x0C]!!.decode(data).first().value
        assertEquals(1726.0, rpm, 0.001)
    }

    @Test fun searchingPrefixIsIgnored() {
        val m = msgs("SEARCHING...\r41 05 7B\r\r")
        assertEquals(0x7B - 40.0, PidCatalog.byPid[0x05]!!.decode(ObdParser.pidData(m, 0x05)!!).first().value, 0.001)
    }

    @Test fun noDataIsAnError() {
        val lines = ObdParser.cleanLines("NO DATA\r\r")
        assertEquals("NO DATA", ObdParser.errorOf(lines))
        assertNull(ObdParser.errorOf(ObdParser.cleanLines("41 0C 1A F8")))
    }

    @Test fun vinMultiFrameCan() {
        val m = msgs("014\r0: 49 02 01 57 56 57\r1: 5A 5A 5A 31 4B 5A 41\r2: 4D 36 39 33 39 36 33\r")
        assertEquals("WVWZZZ1KZAM693963", ObdParser.parseVin(m))
    }

    @Test fun vinMultiLineKwp() {
        val m = msgs("49 02 01 00 00 00 57\r49 02 02 56 57 5A 5A\r49 02 03 5A 31 4B 5A\r49 02 04 41 4D 36 39\r49 02 05 33 39 36 33\r")
        assertEquals("WVWZZZ1KZAM693963", ObdParser.parseVin(m))
    }

    @Test fun dtcCanWithCountByte() {
        val m = msgs("43 02 01 33 04 20\r")
        assertEquals(listOf("P0133", "P0420"), ObdParser.parseDtcs(m, 3))
    }

    @Test fun dtcLegacySevenBytes() {
        val m = msgs("43 01 33 00 00 00 00\r")
        assertEquals(listOf("P0133"), ObdParser.parseDtcs(m, 3))
    }

    @Test fun dtcNoneOnCan() {
        assertEquals(emptyList<String>(), ObdParser.parseDtcs(msgs("43 00\r"), 3))
    }

    @Test fun dtcLetters() {
        assertEquals("C0300", ObdParser.dtcFromBytes(0x43, 0x00))
        assertEquals("B1234", ObdParser.dtcFromBytes(0x92, 0x34))
        assertEquals("U0100", ObdParser.dtcFromBytes(0xC1, 0x00))
        assertEquals("P2463", ObdParser.dtcFromBytes(0x24, 0x63))
    }

    @Test fun supportedPidsBitmap() {
        val sup = ObdParser.supportedPids(msgs("41 00 BE 3F A8 13\r"), 0x00)
        assertTrue(sup.containsAll(listOf(0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0C, 0x0D, 0x20)))
        assertFalse(0x02 in sup)
        assertFalse(0x08 in sup)
    }

    @Test fun supportedPidsFromTwoEcusAreMerged() {
        val sup = ObdParser.supportedPids(msgs("41 00 80 00 00 00\r41 00 40 00 00 00\r"), 0x00)
        assertEquals(setOf(0x01, 0x02), sup)
    }

    @Test fun monitorStatusDiesel() {
        // MIL allumé, 2 défauts ; B = 0x0F (diesel + 3 moniteurs continus supportés, tous complets)
        // C = 0x40 (FAP supporté) ; D = 0x40 (FAP incomplet)
        val st = ObdParser.monitorStatus(listOf(0x82, 0x0F, 0x40, 0x40))!!
        assertTrue(st.milOn)
        assertEquals(2, st.dtcCount)
        assertTrue(st.diesel)
        val fap = st.monitors.first { it.name.startsWith("Filtre à particules") }
        assertTrue(fap.supported)
        assertFalse(fap.complete)
    }

    @Test fun freezeFrame() {
        val m = msgs("42 0C 00 0B B8\r")
        val d = ObdParser.freezeData(m, 0x0C)!!
        assertEquals(750.0, PidCatalog.byPid[0x0C]!!.decode(d).first().value, 0.001)
    }

    @Test fun fapDifferentialPressure() {
        // flags=0x07, delta=+12.34 kPa (0x04D2), entrée 0x2710=100.00 kPa, sortie 0x267C=98.52 kPa
        val d = listOf(0x07, 0x04, 0xD2, 0x27, 0x10, 0x26, 0x7C)
        val r = PidCatalog.byPid[0x7A]!!.decode(d)
        assertEquals(3, r.size)
        assertEquals(12.34, r[0].value, 0.001)
        assertEquals(100.0, r[1].value, 0.001)
        assertEquals(98.52, r[2].value, 0.001)
    }

    @Test fun boostPressure() {
        val d = listOf(0x03, 0x0F, 0xA0, 0x0F, 0x40, 0, 0, 0, 0, 0) // 4000/32=125 ; 3904/32=122
        val r = PidCatalog.byPid[0x70]!!.decode(d)
        assertEquals(125.0, r[0].value, 0.001)
        assertEquals(122.0, r[1].value, 0.001)
    }

    @Test fun railPressureBar() {
        // 0x2710 = 10000 -> 10000*10 kPa = 100000 kPa = 1000 bar
        assertEquals(1000.0, PidCatalog.byPid[0x23]!!.decode(listOf(0x27, 0x10)).first().value, 0.001)
    }

    @Test fun formulaTorqueStyle() {
        assertEquals(1726.0, Formula.eval("((A*256)+B)/4", listOf(0x1A, 0xF8))!!, 0.001)
        assertEquals(45.0, Formula.eval("A-40", listOf(85))!!, 0.001)
        assertEquals(10.0, Formula.eval("{A:3}*10", listOf(0x08))!!, 0.001)
        assertEquals(7.0, Formula.eval("-A+B", listOf(3, 10))!!, 0.001)
        assertEquals(0x1234.toDouble(), Formula.eval("(A<<8)|B", listOf(0x12, 0x34))!!, 0.001)
        assertNull(Formula.eval("A+Z", listOf(1)))
        assertNull(Formula.eval("A+", listOf(1)))
        assertNull(Formula.eval("(A", listOf(1)))
    }

    @Test fun customPidDecode() {
        val p = CustomPid("Test", "2101", "(A*256+B)/10", "bar")
        assertTrue(p.isValid())
        // réponse : 61 01 0A 28  -> (10*256+40)/10 = 260
        assertEquals(260.0, p.decode(listOf(0x61, 0x01, 0x0A, 0x28))!!, 0.001)
        assertNull(p.decode(listOf(0x7F, 0x21, 0x12)))
    }

    @Test fun torqueCsvImport() {
        val csv = "Name,ShortName,ModeAndPID,Equation,Min Value,Max Value,Unit,Header\n" +
            "\"Pression rampe, réelle\",PRail,2101,(A*256+B)/10,0,2000,bar,7E0\n" +
            "Invalide,X,ZZ,A,0,1,,\n"
        val list = CustomPid.parseTorqueCsv(csv)
        assertEquals(1, list.size)
        assertEquals("Pression rampe, réelle", list[0].name)
        assertEquals("7E0", list[0].header)
        assertEquals("2101", list[0].request)
    }

    @Test fun ecuScan() {
        val hits = ObdParser.parseEcuHits(listOf("7E8 06 41 00 BE 3F A8 13", "7E9 06 41 00 98 18 00 01", "SEARCHING..."))
        assertEquals(listOf("7E8", "7E9"), hits.map { it.id })
    }

    @Test fun uds19Decode() {
        val m = listOf(listOf(0x59, 0x02, 0xFF, 0x01, 0x33, 0x00, 0x2F))
        val r = ObdParser.parseDiagDtcs(m)
        assertEquals(1, r.size)
        assertTrue(r[0].startsWith("P0133"))
    }

    @Test fun ascii() {
        assertNotNull(ObdParser.parseCalIds(msgs("49 04 01 41 42 43 00 00 00 00 00 00 00 00 00 00 00 00 00")))
    }
}
