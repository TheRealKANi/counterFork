package me.tsukanov.counter.repository.impl

import android.content.Context
import android.content.SharedPreferences
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import me.tsukanov.counter.domain.CounterEvent
import me.tsukanov.counter.domain.IntegerCounter
import me.tsukanov.counter.domain.exception.CounterException
import me.tsukanov.counter.infrastructure.Actions
import me.tsukanov.counter.infrastructure.BroadcastHelper
import me.tsukanov.counter.repository.SharedPrefsCounterStorage
import me.tsukanov.counter.repository.exceptions.UnsupportedExportVersionException
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import java.util.Collections

@RunWith(MockitoJUnitRunner.Silent::class)
class SharedPrefsCounterStorageTest {

    companion object {
        private const val DEFAULT_COUNTER_NAME = "Test counter"
    }

    @Mock
    private val context: Context? = null

    @Mock
    private val countersSharedPrefs: SharedPreferences? = null

    @Mock
    private val timestampSharedPrefs: SharedPreferences? = null

    @Mock
    private val historySharedPrefs: SharedPreferences? = null

    @Mock
    private val countersPrefsEditor: SharedPreferences.Editor? = null

    @Mock
    private val timestampPrefsEditor: SharedPreferences.Editor? = null

    @Mock
    private val historyPrefsEditor: SharedPreferences.Editor? = null

    @Mock
    private val broadcastHelper: BroadcastHelper? = null

    private var systemUnderTest: SharedPrefsCounterStorage? = null

    @Before
    fun setUp() {
        Mockito.`when`(
            context!!.getSharedPreferences(
                ArgumentMatchers.eq("counters"),
                ArgumentMatchers.anyInt()
            )
        ).thenReturn(countersSharedPrefs)
        Mockito.`when`(countersSharedPrefs!!.edit()).thenReturn(countersPrefsEditor)
        Mockito.`when`(
            countersPrefsEditor!!.putInt(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())
        ).thenReturn(countersPrefsEditor)
        Mockito.`when`(countersPrefsEditor.clear()).thenReturn(countersPrefsEditor)
        Mockito.`when`(countersPrefsEditor.remove(ArgumentMatchers.anyString()))
            .thenReturn(countersPrefsEditor)

        Mockito.`when`(
            context.getSharedPreferences(
                ArgumentMatchers.eq("update-timestamps"),
                ArgumentMatchers.anyInt()
            )
        ).thenReturn(timestampSharedPrefs)
        Mockito.`when`(timestampSharedPrefs!!.edit()).thenReturn(timestampPrefsEditor)
        Mockito.`when`(
            timestampPrefsEditor!!.putString(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()
            )
        ).thenReturn(timestampPrefsEditor)
        Mockito.`when`(timestampPrefsEditor.clear()).thenReturn(timestampPrefsEditor)
        Mockito.`when`(timestampPrefsEditor.remove(ArgumentMatchers.anyString()))
            .thenReturn(timestampPrefsEditor)

        Mockito.`when`(
            context.getSharedPreferences(
                ArgumentMatchers.eq("update-history"),
                ArgumentMatchers.anyInt()
            )
        ).thenReturn(historySharedPrefs)
        Mockito.`when`(historySharedPrefs!!.edit()).thenReturn(historyPrefsEditor)
        Mockito.`when`(
            historyPrefsEditor!!.putString(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()
            )
        ).thenReturn(historyPrefsEditor)
        Mockito.`when`(historyPrefsEditor.clear()).thenReturn(historyPrefsEditor)
        Mockito.`when`(historyPrefsEditor.remove(ArgumentMatchers.anyString()))
            .thenReturn(historyPrefsEditor)

        systemUnderTest = SharedPrefsCounterStorage(
            context,
            broadcastHelper!!, DEFAULT_COUNTER_NAME
        )
    }

    @Test
    fun read_withoutDefaultCounter() {
        Mockito.`when`(countersSharedPrefs!!.all).thenReturn(Collections.emptyMap())

        val output = systemUnderTest!!.readAll(false)
        Assert.assertEquals(0, output.size.toLong())

        Mockito.verify(countersSharedPrefs).all
        Mockito.verify(context)!!.getSharedPreferences("counters", Context.MODE_PRIVATE)
        Mockito.verify(context)!!.getSharedPreferences("update-timestamps", Context.MODE_PRIVATE)
    }

    @Test
    fun read_withDefaultCounter() {
        Mockito.`when`(countersSharedPrefs!!.all).thenReturn(Collections.emptyMap())

        val output = systemUnderTest!!.readAll(true)
        Assert.assertEquals(1, output.size.toLong())

        Mockito.verify(countersSharedPrefs).all
        Mockito.verify(countersPrefsEditor)!!.putInt(DEFAULT_COUNTER_NAME, 0)
        Mockito.verify(countersPrefsEditor)!!.commit()
        // Default-counter creation appends a CREATED history event.
        Mockito.verify(historyPrefsEditor)!!.putString(
            ArgumentMatchers.eq(DEFAULT_COUNTER_NAME),
            ArgumentMatchers.contains("CREATED")
        )
        Mockito.verify(broadcastHelper)!!.sendBroadcast(Actions.COUNTER_SET_CHANGE)
    }

    @Test
    fun readAll_legacyData_bootstrapsCreatedEvent() {
        val testData: Map<String, *> = ImmutableMap.of("legacy", 5)
        Mockito.`when`(countersSharedPrefs!!.all).thenReturn(testData as MutableMap<String, *>?)
        Mockito.`when`(historySharedPrefs!!.contains("legacy")).thenReturn(false)

        systemUnderTest!!.readAll(false)

        Mockito.verify(historyPrefsEditor)!!.putString(
            ArgumentMatchers.eq("legacy"),
            ArgumentMatchers.contains("CREATED")
        )
    }

    @Test
    fun readAll_existingHistory_doesNotBootstrap() {
        val testData: Map<String, *> = ImmutableMap.of("kept", 7)
        Mockito.`when`(countersSharedPrefs!!.all).thenReturn(testData as MutableMap<String, *>?)
        Mockito.`when`(historySharedPrefs!!.contains("kept")).thenReturn(true)

        systemUnderTest!!.readAll(false)

        Mockito.verify(historyPrefsEditor, Mockito.never())!!.putString(
            ArgumentMatchers.eq("kept"),
            ArgumentMatchers.anyString()
        )
    }

    @Test
    @Throws(CounterException::class)
    fun overwriteAll_resetsHistoryToSingleCreatedEvent() {
        systemUnderTest!!.overwriteAll(
            ImmutableList.of(
                IntegerCounter("First counter", 0),
                IntegerCounter("Second counter", 1),
                IntegerCounter("Third counter", -1)
            )
        )

        Mockito.verify(countersPrefsEditor)!!.clear()
        Mockito.verify(countersPrefsEditor)!!.putInt("First counter", 0)
        Mockito.verify(countersPrefsEditor)!!.putInt("Second counter", 1)
        Mockito.verify(countersPrefsEditor)!!.putInt("Third counter", -1)
        Mockito.verify(countersPrefsEditor)!!.commit()

        Mockito.verify(historyPrefsEditor)!!.clear()
        Mockito.verify(historyPrefsEditor)!!.putString(
            ArgumentMatchers.eq("First counter"),
            ArgumentMatchers.contains("CREATED")
        )
        Mockito.verify(historyPrefsEditor)!!.putString(
            ArgumentMatchers.eq("Second counter"),
            ArgumentMatchers.contains("CREATED")
        )
        Mockito.verify(historyPrefsEditor)!!.putString(
            ArgumentMatchers.eq("Third counter"),
            ArgumentMatchers.contains("CREATED")
        )
        Mockito.verify(broadcastHelper)!!.sendBroadcast(Actions.COUNTER_SET_CHANGE)
    }

    @Test
    fun overwrite_withNoCounters() {
        systemUnderTest!!.overwriteAll(emptyList())

        Mockito.verify(countersPrefsEditor)!!.clear()
        Mockito.verify(countersPrefsEditor)!!.commit()
        Mockito.verify(historyPrefsEditor)!!.clear()
        Mockito.verify(broadcastHelper)!!.sendBroadcast(Actions.COUNTER_SET_CHANGE)
    }

    @Test
    fun write_increment_appendsIncrementHistoryEvent() {
        Mockito.`when`(historySharedPrefs!!.getString(ArgumentMatchers.eq("c1"), ArgumentMatchers.any()))
            .thenReturn(null)

        systemUnderTest!!.write(IntegerCounter("c1", 3), CounterEvent.INCREMENT)

        Mockito.verify(countersPrefsEditor)!!.putInt("c1", 3)
        Mockito.verify(historyPrefsEditor)!!.putString(
            ArgumentMatchers.eq("c1"),
            ArgumentMatchers.contains("INCREMENT")
        )
        Mockito.verify(broadcastHelper)!!.sendBroadcast(Actions.COUNTER_SET_CHANGE)
    }

    @Test
    fun write_edited_appendsEditedHistoryEvent() {
        Mockito.`when`(historySharedPrefs!!.getString(ArgumentMatchers.eq("c1"), ArgumentMatchers.any()))
            .thenReturn(null)

        systemUnderTest!!.write(IntegerCounter("c1", 10), CounterEvent.EDITED)

        Mockito.verify(historyPrefsEditor)!!.putString(
            ArgumentMatchers.eq("c1"),
            ArgumentMatchers.contains("EDITED")
        )
    }

    @Test
    fun delete_writesDeletedTombstoneAndRemovesValueAndTimestamp() {
        Mockito.`when`(countersSharedPrefs!!.getInt("c1", 0)).thenReturn(42)
        Mockito.`when`(historySharedPrefs!!.getString(ArgumentMatchers.eq("c1"), ArgumentMatchers.any()))
            .thenReturn("[]")

        systemUnderTest!!.delete("c1")

        Mockito.verify(historyPrefsEditor)!!.putString(
            ArgumentMatchers.eq("c1"),
            ArgumentMatchers.contains("DELETED")
        )
        Mockito.verify(countersPrefsEditor)!!.remove("c1")
        Mockito.verify(timestampPrefsEditor)!!.remove("c1")
        // History prefs itself should NOT be cleared by delete.
        Mockito.verify(historyPrefsEditor, Mockito.never())!!.remove("c1")
        Mockito.verify(broadcastHelper)!!.sendBroadcast(Actions.COUNTER_SET_CHANGE)
    }

    @Test
    fun wipe_clearsAllThreeStoresIncludingHistory() {
        systemUnderTest!!.wipe()

        Mockito.verify(countersPrefsEditor)!!.clear()
        Mockito.verify(timestampPrefsEditor)!!.clear()
        Mockito.verify(historyPrefsEditor)!!.clear()
        Mockito.verify(broadcastHelper)!!.sendBroadcast(Actions.COUNTER_SET_CHANGE)
    }

    @Test
    @Throws(Exception::class)
    fun toCsv_v2_writesVersionHeaderAndPerCounterBlocks() {
        val testData: Map<String, *> = ImmutableMap.of("first", 0, "second, ok", -1)
        Mockito.`when`(countersSharedPrefs!!.all).thenReturn(testData as MutableMap<String, *>?)
        Mockito.`when`(historySharedPrefs!!.contains(ArgumentMatchers.anyString())).thenReturn(true)
        Mockito.`when`(
            historySharedPrefs.getString(ArgumentMatchers.anyString(), ArgumentMatchers.any())
        ).thenReturn("[]")

        val output = systemUnderTest!!.toCsv()

        Assert.assertTrue("Expected version header", output.contains("# Version: 2"))
        Assert.assertTrue("Expected ExportedAt header", output.contains("# ExportedAt:"))
        Assert.assertTrue(
            "Expected at least one [Counter] section",
            output.contains("[Counter]")
        )
        Assert.assertTrue(
            "Expected at least one [History] section",
            output.contains("[History]")
        )
        Assert.assertTrue(
            "Expected [Counter] CSV header line",
            output.contains("\"Name\",\"Value\",\"LastUpdate\"")
        )
        Assert.assertTrue(
            "Expected [History] CSV header line",
            output.contains("\"Event\",\"Value\",\"Timestamp\"")
        )
        Assert.assertTrue(output.contains("\"first\""))
        Assert.assertTrue(output.contains("\"second, ok\""))
    }

    @Test(expected = UnsupportedExportVersionException::class)
    fun fromCsv_unknownVersion_throws() {
        val payload = """
            # CounterFork Export
            # Version: 99
            [Counter]
            Name,Value,LastUpdate
            x,0,
            [History]
            Event,Value,Timestamp
        """.trimIndent()

        systemUnderTest!!.fromCsv(payload)
    }

    @Test(expected = UnsupportedExportVersionException::class)
    fun fromCsv_missingVersion_throws() {
        val payload = """
            [Counter]
            Name,Value,LastUpdate
            x,0,
        """.trimIndent()

        systemUnderTest!!.fromCsv(payload)
    }

    @Test
    fun fromCsv_v2_writesCountersAndHistoryToPrefs() {
        val payload = """
            # CounterFork Export
            # Version: 2

            [Counter]
            "Name","Value","LastUpdate"
            "alpha","7",""

            [History]
            "Event","Value","Timestamp"
            "CREATED","0","20260517T100000+0200"
            "INCREMENT","1","20260517T100100+0200"
            "EDITED","7","20260517T100200+0200"
        """.trimIndent()

        systemUnderTest!!.fromCsv(payload)

        Mockito.verify(countersPrefsEditor)!!.clear()
        Mockito.verify(timestampPrefsEditor)!!.clear()
        Mockito.verify(historyPrefsEditor)!!.clear()
        Mockito.verify(countersPrefsEditor)!!.putInt("alpha", 7)
        Mockito.verify(historyPrefsEditor)!!.putString(
            ArgumentMatchers.eq("alpha"),
            ArgumentMatchers.contains("INCREMENT")
        )
        Mockito.verify(broadcastHelper)!!.sendBroadcast(Actions.COUNTER_SET_CHANGE)
    }
}
